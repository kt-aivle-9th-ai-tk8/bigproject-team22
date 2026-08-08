package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.event.ReportGenerationRequested;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportGenerationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 생성 오케스트레이션의 분기를 검증한다. 리스너를 직접 생성해 호출하므로 {@code @Async} 프록시가 끼지 않아
 * 동기·결정적으로 돈다(트랜잭션 조각은 {@link ReportGenerationService} 목으로 대체).
 */
@ExtendWith(MockitoExtension.class)
class ReportGenerationListenerTest {

    private static final ReportGenerationRequested EVENT = new ReportGenerationRequested(7L);

    @Mock
    ReportGenerationService generationService;
    @Mock
    ReportGenerationPort generationPort;
    @InjectMocks
    ReportGenerationListener listener;

    private void enabled() {
        when(generationPort.isEnabled()).thenReturn(true);
    }

    private ReportGenerationService.Dispatch dispatch() {
        return new ReportGenerationService.Dispatch(new ReportGenerationTarget("operation", 2), "제목");
    }

    @Test
    @DisplayName("에이전트 미설정이면 PROCESSING 전이도 하지 않고 건너뛴다(PENDING 유지)")
    void agentDisabled_skips() {
        when(generationPort.isEnabled()).thenReturn(false);

        listener.onReportGenerationRequested(EVENT);

        verify(generationService, never()).markProcessing(any());
        verify(generationPort, never()).generate(any());
    }

    @Test
    @DisplayName("본문이 생성되면 적재한다(GENERATED)")
    void generated_applies() {
        enabled();
        when(generationService.markProcessing(7L)).thenReturn(dispatch());
        when(generationPort.generate(any())).thenReturn(new ReportGenerationResult(true, "# 본문", "적합"));

        listener.onReportGenerationRequested(EVENT);

        verify(generationService).applyGenerated(7L, "제목", "# 본문");
    }

    @Test
    @DisplayName("대상없음/본문없음이면 적재하지 않는다(PROCESSING 유지)")
    void notGenerated_keepsProcessing() {
        enabled();
        when(generationService.markProcessing(7L)).thenReturn(dispatch());
        when(generationPort.generate(any())).thenReturn(ReportGenerationResult.notGenerated());

        listener.onReportGenerationRequested(EVENT);

        verify(generationService, never()).applyGenerated(any(), any(), any());
    }

    @Test
    @DisplayName("found=true 여도 draft 가 비면 적재하지 않는다")
    void blankDraft_notApplied() {
        enabled();
        when(generationService.markProcessing(7L)).thenReturn(dispatch());
        when(generationPort.generate(any())).thenReturn(new ReportGenerationResult(true, "  ", "적합"));

        listener.onReportGenerationRequested(EVENT);

        verify(generationService, never()).applyGenerated(any(), any(), any());
    }

    @Test
    @DisplayName("경쟁 삭제(markProcessing=null)면 에이전트를 부르지 않는다")
    void missingReport_noAgentCall() {
        enabled();
        when(generationService.markProcessing(7L)).thenReturn(null);

        listener.onReportGenerationRequested(EVENT);

        verify(generationPort, never()).generate(any());
        verify(generationService, never()).applyGenerated(any(), any(), any());
    }

    @Test
    @DisplayName("에이전트 호출이 예외를 던져도 삼키고 적재하지 않는다(PROCESSING 유지)")
    void agentThrows_swallowed() {
        enabled();
        when(generationService.markProcessing(7L)).thenReturn(dispatch());
        when(generationPort.generate(any())).thenThrow(new RuntimeException("timeout"));

        listener.onReportGenerationRequested(EVENT);

        verify(generationService, never()).applyGenerated(eq(7L), any(), any());
    }
}
