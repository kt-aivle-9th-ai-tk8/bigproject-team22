package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.DefectReportPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.event.InspectionUploadCompleted;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 점검 생성/업로드 완료 유스케이스.
 * <p>
 * 생성은 점검·결함 보고서 행과 presigned URL 발급을 <b>한 트랜잭션</b>으로 처리한다 — presign 은 로컬
 * 서명 연산이라(네트워크 없음) 트랜잭션 안에서 수행해도 무해하고, 실패(저장소 미설정 503) 시 행이 함께
 * 롤백되어 고아 점검이 남지 않는다.
 * <p>
 * 업로드 완료는 상태 전이와 아웃박스 기록을 한 트랜잭션으로 묶는다 — 추론 요청의 유실 방지가 아웃박스의
 * 존재 이유다. 실제 발사(SageMaker Async)는 릴레이(폴러)가 별도로 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionCommandService {

    /** 요청 1건당 presigned URL 발급 상한(폭주 방지). FE 업로더의 현실 사용량보다 넉넉히 잡는다. */
    static final int MAX_TOTAL_IMAGES = 200;

    /** 부위별 이미지 상한. 운영 가정(통상 10장, 최대 20장)을 코드로 강제한다. */
    static final int MAX_IMAGES_PER_SIDE = 20;

    static final String AGGREGATE_TYPE = "Inspection";
    static final String EVENT_IMAGE_UPLOADED = "InspectionImageUploaded";

    private final InspectionRepository inspectionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InspectionAssetPort assetPort;
    private final DefectReportPort reportPort;
    private final InspectionStoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final ThumbnailService thumbnailService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 드론 점검 세션 생성: 세션당 결함 보고서 1건 + 터빈마다 점검 1행 + 블레이드·부위별 presigned PUT URL 발급.
     * <p>
     * 미담당/미존재 단지는 404 로 은닉된다(가드 규약). 요청 터빈이 그 단지 소속이 아니면 404(명세),
     * 블레이드가 그 터빈 소속이 아니면 {@link ErrorCode#INVALID_INSPECTION_TARGET}(400).
     * 촬영 기간(inspection_start/end)은 세션의 모든 점검과 보고서 기간에 동일하게 적용된다.
     */
    @Transactional
    public CreateInspectionResult create(Long userId, boolean admin, CreateInspectionCommand command) {
        assetPort.checkWindFarmAccess(userId, admin, command.windFarmId()); // 미담당/미존재 → 404 은닉
        validate(command);

        // 터빈 소속 검증(명세: turbine_id not found → 404). 미존재/타 단지 소속을 구분하지 않는다.
        for (CreateInspectionCommand.TurbineSpec turbine : command.turbines()) {
            if (!command.windFarmId().equals(assetPort.windFarmIdOf(turbine.turbineId()))) {
                throw new BusinessException(ErrorCode.TURBINE_NOT_FOUND);
            }
        }

        Long reportId = reportPort.createDefectReport(
                command.windFarmId(), command.inspectionStart(), command.inspectionEnd(), userId, command.context());

        List<CreateInspectionResult.TurbineResult> turbineResults = new ArrayList<>();
        for (CreateInspectionCommand.TurbineSpec turbine : command.turbines()) {
            Set<Long> bladeIds = assetPort.bladesOf(turbine.turbineId()).stream()
                    .map(InspectionAssetPort.BladeRef::id)
                    .collect(Collectors.toSet());
            for (CreateInspectionCommand.BladeSpec blade : turbine.blades()) {
                if (!bladeIds.contains(blade.bladeId())) {
                    throw new BusinessException(ErrorCode.INVALID_INSPECTION_TARGET); // 그 터빈에 없는 블레이드
                }
            }

            Inspection inspection = inspectionRepository.save(Inspection.request(
                    turbine.turbineId(), userId, reportId, command.inspectionStart(), command.inspectionEnd()));

            List<CreateInspectionResult.BladeResult> bladeResults = new ArrayList<>();
            for (CreateInspectionCommand.BladeSpec blade : turbine.blades()) {
                bladeResults.add(new CreateInspectionResult.BladeResult(
                        blade.bladeId(),
                        presignSide(inspection.getId(), blade.bladeId(), PartSide.LE, blade.leadingEdgeCount()),
                        presignSide(inspection.getId(), blade.bladeId(), PartSide.PS, blade.pressureSideCount()),
                        presignSide(inspection.getId(), blade.bladeId(), PartSide.SS, blade.suctionSideCount()),
                        presignSide(inspection.getId(), blade.bladeId(), PartSide.TE, blade.trailingEdgeCount())));
            }
            turbineResults.add(new CreateInspectionResult.TurbineResult(
                    turbine.turbineId(), inspection.getId(), bladeResults));
        }
        return new CreateInspectionResult(command.windFarmId(), turbineResults, reportId);
    }

    /** 한 (블레이드, 부위)의 presigned URL 을 count 만큼 발급한다(count 0 이면 빈 목록). */
    private List<String> presignSide(long inspectionId, long bladeId, PartSide partSide, int count) {
        List<String> urls = new ArrayList<>(count);
        for (int seq = 1; seq <= count; seq++) {
            urls.add(storagePort.presignImageUpload(inspectionId, bladeId, partSide, seq).url());
        }
        return urls;
    }

    /**
     * 업로드 완료 통보. 상태를 INSPECTING 으로 올리고, <b>실제로 업로드된</b> 이미지(S3 LIST 기준)마다
     * 아웃박스 행을 기록한다 — FE 신고를 신뢰하지 않고 원천(S3)을 기준으로 삼는다.
     * <p>
     * S3 LIST 는 짧은 단건 호출이라 트랜잭션 안에서 수행한다(실패 시 전이·기록이 함께 롤백되어 재시도 가능).
     */
    @Transactional
    public int completeUpload(Long userId, boolean admin, Long inspectionId, Integer expectedCount) {
        // 쓰기 잠금으로 동시 완료 통보를 직렬화한다 — 잠금 없이는 두 요청이 모두 UPLOADING 을 읽고
        // markInspecting 을 통과해 아웃박스(추론 요청)가 중복 기록된다. 후속 요청은 잠금 해제 후
        // INSPECTING 을 보고 D002 로 거부된다.
        Inspection inspection = inspectionRepository.findByIdForUpdate(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_NOT_FOUND));
        // 완료 통보는 '소유자(세션 생성자)'만 할 수 있다 — FE 의 "업로드 끝" 안내일 뿐 검증 주체는 BE 이며,
        // 동일 단지 담당자라도 타인의 진행 중 세션을 부분 업로드 상태로 조기 종료(비가역 전이)시킬 수 없어야 한다.
        // ADMIN 도 예외를 두지 않는다(업로더 클라이언트 흐름에 결속된 상태 변경). 비소유자에게는 존재를 은닉한다.
        if (inspection.getUserId() == null || !inspection.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INSPECTION_NOT_FOUND);
        }
        try {
            // 배정이 해제된 소유자의 잔여 조작도 막는다(담당 기반 접근 모델과 정합).
            assetPort.checkTurbineAccess(userId, admin, inspection.getTurbineId());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INSPECTION_NOT_FOUND); // 동일하게 은닉(404)
        }

        inspection.markInspecting(); // UPLOADING 이 아니면 D002(명세상 400)

        List<InspectionStoragePort.UploadedImage> images = storagePort.listUploadedImages(inspectionId);
        // 수량 대조를 빈 목록 검사보다 먼저 한다 — "N장 통보, 실측 0장"은 입력 오류가 아니라 수량 불일치이고,
        // FE 가 원인(일부/전부 PUT 실패)을 알아야 재시도할 수 있다.
        if (expectedCount != null && images.size() != expectedCount) {
            // 통보 값은 기대값일 뿐 진실은 S3 다. 다르면 일부 PUT 이 실패했거나 아직 끝나지 않은 것이므로
            // 거부해 재시도하게 한다 — 조용히 진행하면 누락분이 영원히 추론되지 않는다(전이·기록 모두 롤백).
            log.warn("점검 {} 업로드 수 불일치 — 통보 {}장, S3 실측 {}장", inspectionId, expectedCount, images.size());
            throw new BusinessException(ErrorCode.UPLOAD_COUNT_MISMATCH);
        }
        if (images.isEmpty()) {
            // 수량 통보가 없었는데 한 장도 없는 완료 통보는 성립하지 않는다(전이도 롤백된다).
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<OutboxEvent> events = images.stream()
                .map(image -> OutboxEvent.pending(
                        AGGREGATE_TYPE, String.valueOf(inspectionId), EVENT_IMAGE_UPLOADED,
                        payloadJson(inspectionId, image)))
                .toList();
        outboxEventRepository.saveAll(events);
        // 커밋 이후 썸네일 생성이 돌도록 알린다(AFTER_COMMIT). 실패해도 점검 진행에는 영향이 없다.
        eventPublisher.publishEvent(new InspectionUploadCompleted(inspectionId));
        log.info("점검 {} 업로드 완료 — 이미지 {}건 아웃박스 기록.", inspectionId, events.size());
        return events.size();
    }

    /**
     * 점검 1건의 썸네일 생성을 다시 건다(운영·보정용).
     * <p>
     * 업로드 완료 시 자동으로 돌지만, ① 그 기능이 없던 시절에 올라간 <b>기존 점검</b>과 ② 재시작으로 대기
     * 큐가 날아간 경우를 회수할 경로가 필요하다. 이미 있는 썸네일은 건너뛰므로 <b>몇 번을 불러도 안전</b>하다.
     * <p>
     * 인가는 업로드 이미지 조회와 같은 규약이다 — 미담당/미존재는 404 로 은닉한다.
     * 실제 생성은 비동기라 이 메서드는 접수만 하고 즉시 반환한다.
     */
    @Transactional(readOnly = true)
    public void requestThumbnailGeneration(Long userId, boolean admin, Long inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_NOT_FOUND));
        try {
            assetPort.checkTurbineAccess(userId, admin, inspection.getTurbineId());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INSPECTION_NOT_FOUND);
        }
        thumbnailService.generate(inspectionId);
    }

    private void validate(CreateInspectionCommand command) {
        if (command.inspectionStart() == null || command.inspectionEnd() == null
                || command.inspectionEnd().isBefore(command.inspectionStart())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 촬영 기간 필수 + 역전 금지
        }
        if (command.turbines() == null || command.turbines().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        long distinctTurbines = command.turbines().stream()
                .map(CreateInspectionCommand.TurbineSpec::turbineId).distinct().count();
        if (distinctTurbines != command.turbines().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 중복 터빈 = 점검 중복 생성 요청
        }
        long total = 0; // int 합산은 큰 입력에서 오버플로우해 상한 검사를 우회할 수 있다 — long + 즉시 거부
        for (CreateInspectionCommand.TurbineSpec turbine : command.turbines()) {
            if (turbine.blades() == null || turbine.blades().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            // 한 터빈 안의 중복 블레이드는 같은 S3 키에 URL 을 중복 발급시켜 업로드가 조용히 덮어써진다
            // (이미지 유실 → 추론 누락). 중복 터빈 규칙과 동일하게 거부한다.
            long distinctBlades = turbine.blades().stream()
                    .map(CreateInspectionCommand.BladeSpec::bladeId).distinct().count();
            if (distinctBlades != turbine.blades().size()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            for (CreateInspectionCommand.BladeSpec blade : turbine.blades()) {
                // 운영 가정(부위당 최대 20장)을 코드로 강제한다 — 가정만 믿으면 폭주 입력이 그대로 통과한다.
                requireCountInRange(blade.leadingEdgeCount());
                requireCountInRange(blade.pressureSideCount());
                requireCountInRange(blade.suctionSideCount());
                requireCountInRange(blade.trailingEdgeCount());
                total += blade.total();
                if (total > MAX_TOTAL_IMAGES) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT); // 초과 즉시 거부(불필요한 순회 방지)
                }
            }
        }
        if (total < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 이미지 0장 세션은 성립하지 않는다
        }
    }

    private static void requireCountInRange(int count) {
        if (count < 0 || count > MAX_IMAGES_PER_SIDE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 릴레이가 발행에 쓰는 본문. 전역 snake_case 전략으로 {@code {inspection_id, image_key, blade_id, part_side}}. */
    private String payloadJson(Long inspectionId, InspectionStoragePort.UploadedImage image) {
        return objectMapper.writeValueAsString(
                new ImageUploadedPayload(inspectionId, image.key(), image.bladeId(), image.partSide().name()));
    }

    /** 아웃박스 payload 스키마(직렬화 전용). */
    record ImageUploadedPayload(Long inspectionId, String imageKey, Long bladeId, String partSide) {
    }
}
