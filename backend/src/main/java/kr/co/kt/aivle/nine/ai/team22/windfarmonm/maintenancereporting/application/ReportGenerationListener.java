package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.event.ReportGenerationRequested;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportGenerationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 보고서 생성 파이프라인의 오케스트레이터.
 * <p>
 * 생성요청 트랜잭션이 <b>커밋된 뒤</b>({@link TransactionPhase#AFTER_COMMIT}) 실행된다 — 커밋 전이면
 * 리스너가 아직 없는 행을 읽는다. 그리고 <b>별도 executor</b>({@code reportExecutor})에서 비동기로 돈다 —
 * 에이전트 동기 호출이 길 수 있어 요청 스레드(및 프론트→BE ALB)를 붙잡지 않기 위해서다.
 * <p>
 * 트랜잭션 조각({@code markProcessing}/{@code applyGenerated})은 {@link ReportGenerationService} 를
 * <b>외부 호출</b>해 프록시가 적용되게 하고, 그 사이의 긴 외부 호출({@link ReportGenerationPort#generate})은
 * 트랜잭션 밖에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationListener {

    private final ReportGenerationService generationService;
    private final ReportGenerationPort generationPort;

    @Async("reportExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportGenerationRequested(ReportGenerationRequested event) {
        Long reportId = event.reportId();

        // 에이전트 미설정(base-url blank)이면 생성을 건너뛰고 PENDING 으로 남긴다(앱·생성요청은 정상).
        if (!generationPort.isEnabled()) {
            log.info("report-agent 미설정 — 보고서 {} 는 PENDING 으로 남긴다(본문 생성 skip).", reportId);
            return;
        }

        ReportGenerationService.Dispatch dispatch = generationService.markProcessing(reportId); // tx
        if (dispatch == null) {
            return; // 경쟁 삭제 등
        }

        ReportGenerationResult result;
        try {
            result = generationPort.generate(dispatch.target()); // 긴 외부 동기 호출 — 트랜잭션 밖
        } catch (RuntimeException e) {
            // 어댑터가 이미 삼키지만 방어적으로 한 번 더 — 실패해도 보고서는 PROCESSING 으로 남고 회수 가능.
            // throwable 을 넘겨 stack trace 를 보존한다(@Async 라 호출 스택이 끊기기 쉬움).
            log.warn("보고서 {} 생성 호출 실패", reportId, e);
            return;
        }

        if (result.found() && StringUtils.hasText(result.draft())) {
            generationService.applyGenerated(reportId, dispatch.title(), result.draft()); // tx
            log.info("보고서 {} 생성 완료(verdict={}).", reportId, result.verdict());
        } else {
            // 대상없음/본문없음 — FAILED 상태를 두지 않으므로 PROCESSING 으로 남긴다(PATCH/재요청으로 회수).
            log.warn("보고서 {} 본문 미생성(found={}, verdict={}) — PROCESSING 유지.",
                    reportId, result.found(), result.verdict());
        }
    }
}
