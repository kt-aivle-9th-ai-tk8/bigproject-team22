package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InferenceDispatchPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 아웃박스 릴레이의 트랜잭션 조각. PENDING 행을 SageMaker Async 추론으로 접수(발사)한다.
 * <p>
 * 회차 오케스트레이션(배치 순회)은 인프라 스케줄러({@code OutboxRelayScheduler})가 하고, 이 서비스는
 * {@link #publishOne} 을 <b>외부에서</b> 호출받는다(self-invocation 이면 @Transactional 프록시가 적용되지
 * 않는다 — ReportGenerationService 와 동일 구조). 발사 실패 행은 PENDING 으로 남아 다음 회차에 재시도된다.
 * 같은 행이 두 번 발사되어도 inferenceId(행 id)가 같아 결과 처리 쪽 멱등 가드(COMPLETED skip)가 흡수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    /** 한 회차 처리 상한. 폭주 시에도 다음 회차가 이어받는다. */
    public static final int BATCH_SIZE = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final InferenceDispatchPort dispatchPort;
    private final InspectionStoragePort storagePort;
    private final ObjectMapper objectMapper;

    /** 추론 엔드포인트가 설정돼 있는지. 미설정이면 스케줄러가 회차를 통째로 건너뛴다(휴면). */
    public boolean isDispatchConfigured() {
        return dispatchPort.isConfigured();
    }

    /** 이번 회차에 발사할 PENDING 행 id 목록(발생 순서). */
    @Transactional(readOnly = true)
    public List<Long> pendingEventIds() {
        return outboxEventRepository.findPendingBatch(BATCH_SIZE).stream()
                .map(OutboxEvent::getId)
                .toList();
    }

    /**
     * 행 1건 발사: 접수(짧은 API 호출)와 PUBLISHED 전이를 한 트랜잭션으로 묶는다.
     * 접수 후 커밋이 실패하면 다음 회차에 같은 inferenceId 로 재접수된다 — 결과 쪽 멱등 가드가 흡수.
     */
    @Transactional
    public void publishOne(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return; // 경쟁 처리됨 — 조용히 종료
        }
        JsonNode payload = objectMapper.readTree(event.getPayload());
        String imageKey = payload.path("image_key").asString(null);
        if (imageKey == null || imageKey.isBlank()) {
            // 규약 밖 payload 는 재시도해도 소용없다 — FAILED 로 빼서 무한 재시도를 막는다.
            log.error("아웃박스 {} payload 에 image_key 가 없다 — FAILED 처리: {}", eventId, event.getPayload());
            event.markFailed();
            return;
        }
        dispatchPort.dispatch(storagePort.imageS3Uri(imageKey), String.valueOf(event.getId()));
        event.markPublished();
    }
}
