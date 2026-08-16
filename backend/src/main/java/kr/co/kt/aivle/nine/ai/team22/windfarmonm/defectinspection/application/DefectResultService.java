package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.DefectReportPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.DefectRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 추론 결과 통보(SQS 메시지 1건 = 이미지 1장) 처리의 트랜잭션 조각.
 * <p>
 * 통보는 SNS raw delivery 라 봉투가 없고, success/error 토픽이 <b>같은 큐</b>로 들어오므로
 * 본문의 {@code invocationStatus}(Completed/Failed)로 판별한다. 상관키는 {@code inferenceId} = 아웃박스 행 id.
 * <p>
 * SQS 는 at-least-once 라 중복 배달이 필연 — 아웃박스 행이 이미 종결(COMPLETED/FAILED)이면 건너뛴다(멱등).
 * 폴러가 단일 스레드(ShedLock)로 메시지를 순차 처리하므로, "마지막 이미지 완료 → INSPECTED 전이" 판정에
 * 동시성 경합이 없다(병렬 소비로 바꾸면 이 판정부터 다시 설계할 것).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefectResultService {

    /** 심각도 계약 범위(V5 주석: 1~4). 범위 검증은 애플리케이션 담당이다. */
    private static final int MIN_SEVERITY = 1;
    private static final int MAX_SEVERITY = 4;

    private final OutboxEventRepository outboxEventRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final InspectionStoragePort storagePort;
    private final DefectReportPort reportPort;
    private final ObjectMapper objectMapper;

    /**
     * 통보 1건을 처리한다.
     *
     * @param messageBody SQS 메시지 본문(SageMaker Async 통보 JSON, raw)
     * @return true 면 처리 종결(메시지 삭제 가능). 재시도가 무의미한 형식 오류도 true(삭제) 다 —
     *         재배달해 봐야 같은 실패를 반복하고 DLQ 만 오염시킨다. false 는 일시 오류(재배달 대상)가 아니라
     *         예외로 표현된다(폴러가 삭제하지 않음).
     */
    @Transactional
    public boolean process(String messageBody) {
        JsonNode notification = objectMapper.readTree(messageBody);
        String inferenceId = notification.path("inferenceId").asString(null);
        if (inferenceId == null) {
            log.warn("inferenceId 가 없는 통보 — 파이프라인 밖 메시지로 보고 폐기: {}", truncate(messageBody));
            return true;
        }
        OutboxEvent event = findEvent(inferenceId);
        if (event == null) {
            log.warn("통보의 inferenceId={} 에 해당하는 아웃박스 행이 없다 — 폐기", inferenceId);
            return true;
        }
        if (event.isCompleted()) {
            return true; // at-least-once 중복 배달 — 이미 처리됨
        }

        String status = notification.path("invocationStatus").asString("");
        if ("Completed".equals(status)) {
            applyResult(event, notification);
        } else {
            // Failed 등. 실패 사유는 로그로만 — 재추론은 운영자 판단(아웃박스 FAILED 조회)으로 한다.
            log.error("추론 실패 통보 inferenceId={} status={} reason={}",
                    inferenceId, status, notification.path("failureReason").asString(""));
            event.markFailed();
        }

        finishInspectionIfDone(event);
        return true;
    }

    private OutboxEvent findEvent(String inferenceId) {
        try {
            return outboxEventRepository.findById(Long.parseLong(inferenceId)).orElse(null);
        } catch (NumberFormatException e) {
            return null; // 우리 릴레이가 만든 형식이 아니다
        }
    }

    /** 결과 JSON([{bbox,defect_type,confidence,severity}])을 읽어 결함 행으로 적재하고 행을 종결한다. */
    private void applyResult(OutboxEvent event, JsonNode notification) {
        String outputLocation = notification.path("responseParameters").path("outputLocation").asString(null);
        if (outputLocation == null) {
            log.error("Completed 통보에 outputLocation 이 없다 — 아웃박스 {} FAILED 처리", event.getId());
            event.markFailed();
            return;
        }
        JsonNode payload = objectMapper.readTree(event.getPayload());
        long inspectionId = payload.path("inspection_id").asLong();
        long bladeId = payload.path("blade_id").asLong();
        PartSide partSide = parsePartSide(payload.path("part_side").asString(null));
        String imageKey = payload.path("image_key").asString(null);

        JsonNode detections = objectMapper.readTree(storagePort.readJson(outputLocation));
        List<Defect> defects = new ArrayList<>();
        for (JsonNode detection : detections) {
            // bbox 는 [x1,y1,x2,y2] — DB 는 (x,y,w,h) 로 저장한다.
            JsonNode bbox = detection.path("bbox");
            Double x1 = doubleOrNull(bbox, 0);
            Double y1 = doubleOrNull(bbox, 1);
            Double x2 = doubleOrNull(bbox, 2);
            Double y2 = doubleOrNull(bbox, 3);
            defects.add(Defect.detected(
                    inspectionId, bladeId,
                    detection.path("defect_type").asString("UNKNOWN"),
                    parseSeverity(detection.path("severity").asString(null)),
                    partSide,
                    x1, y1,
                    (x1 != null && x2 != null) ? x2 - x1 : null,
                    (y1 != null && y2 != null) ? y2 - y1 : null,
                    detection.path("confidence").isNumber() ? detection.path("confidence").asDouble() : null,
                    imageKey));
        }
        if (!defects.isEmpty()) {
            defectRepository.saveAll(defects);
        }
        event.markCompleted(); // 결함 0건(정상 이미지)도 정상 종결이다
        log.info("추론 결과 적재 완료 inspection={} image={} 결함 {}건", inspectionId, imageKey, defects.size());
    }

    /**
     * 점검의 모든 이미지가 종결됐으면 INSPECTED 로 전이하고, <b>성공한 추론이 하나라도 있을 때만</b>
     * 결함 보고서 본문 생성을 요청한다.
     * <p>
     * 전이 기준은 "미완료(PENDING/PUBLISHED) 없음"이다 — FAILED 를 미완료로 치면 이미지 한 장의 영구
     * 실패(손상 파일 등)로 점검이 INSPECTING 에 영원히 갇힌다. 추론 실패는 재시도 경로가 없으므로
     * 그 정체는 운영자 개입 전까지 풀리지 않는다.
     * <p>
     * 다만 <b>전부 실패한 세션은 보고서를 만들지 않는다</b> — 결함 0건이 '정상'인지 '추론 실패'인지
     * 구분되지 않은 채 보고서가 나가면 없는 안전을 보고하는 셈이 된다. 일부만 실패한 경우는 확보된
     * 결함으로 보고서를 만들되 경고를 남긴다(부분 결과가 무결과보다 낫다).
     */
    private void finishInspectionIfDone(OutboxEvent event) {
        String aggregateType = event.getAggregateType();
        String aggregateId = event.getAggregateId();
        if (outboxEventRepository.existsUnfinishedByAggregate(aggregateType, aggregateId)) {
            return; // 아직 결과 대기 중인 이미지가 남았다
        }
        long inspectionId = Long.parseLong(aggregateId);
        Inspection inspection = inspectionRepository.findById(inspectionId).orElse(null);
        if (inspection == null) {
            log.warn("점검 {} 이 없다(경쟁 삭제?) — 전이/생성 생략", inspectionId);
            return;
        }
        inspection.markInspected();

        long failed = outboxEventRepository.countFailedByAggregate(aggregateType, aggregateId);
        boolean anySucceeded = outboxEventRepository.existsCompletedByAggregate(aggregateType, aggregateId);
        if (!anySucceeded) {
            log.error("점검 {} 의 추론이 전부 실패({}건) — INSPECTED 로 닫되 보고서는 생성하지 않는다"
                    + "(결함 0건이 정상인지 실패인지 구분되지 않는다)", inspectionId, failed);
            return;
        }
        if (failed > 0) {
            log.warn("점검 {} 에 추론 실패 {}건이 섞였다 — 확보된 결함으로 보고서를 생성한다(부분 결과)",
                    inspectionId, failed);
        }
        if (inspection.getReportId() != null) {
            // 이 트랜잭션이 커밋된 뒤(AFTER_COMMIT) 생성 파이프라인이 돈다 — 커밋 전 결함으로 생성하지 않는다.
            reportPort.requestGeneration(inspection.getReportId());
        }
        log.info("점검 {} 결과 적재 종결 — INSPECTED 전이, 보고서 {} 생성 요청", inspectionId, inspection.getReportId());
    }

    private static PartSide parsePartSide(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PartSide.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * CNN 심각도. 모델은 체크포인트의 class_names 값을 그대로 내보내므로 숫자가 아닐 수 있다
     * (inference.py 확인 — AI 팀과 계약 정렬 중). 숫자가 아니거나 계약 범위(1~4) 밖이면
     * null 로 두고 흔적을 남긴다 — 범위 밖 값을 그대로 넣으면 이미지 그룹의 max_severity 가 오염된다.
     */
    private static Integer parseSeverity(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            int severity = Integer.parseInt(raw.trim());
            if (severity < MIN_SEVERITY || severity > MAX_SEVERITY) {
                log.warn("계약 범위({}~{}) 밖 severity: {} — null 로 적재", MIN_SEVERITY, MAX_SEVERITY, raw);
                return null;
            }
            return severity;
        } catch (NumberFormatException e) {
            log.warn("숫자가 아닌 severity 클래스명: {} — null 로 적재", raw);
            return null;
        }
    }

    private static Double doubleOrNull(JsonNode array, int index) {
        JsonNode node = array.path(index);
        return node.isNumber() ? node.asDouble() : null;
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
