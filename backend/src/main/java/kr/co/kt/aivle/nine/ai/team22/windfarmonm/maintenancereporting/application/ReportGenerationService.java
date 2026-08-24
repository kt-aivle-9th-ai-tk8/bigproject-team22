package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보고서 생성 파이프라인의 <b>트랜잭션 경계</b> 두 조각을 제공한다: 상태를 PROCESSING 으로 올리며
 * 에이전트 호출 대상({@link ReportGenerationTarget})을 계산하는 {@link #markProcessing}, 그리고 회신한
 * 제목·본문을 적재하는 {@link #applyGenerated}. 그 사이의 <b>긴 외부 호출은 트랜잭션 밖</b>에서 일어나야 하므로
 * (DB 커넥션을 붙잡지 않도록), 오케스트레이션은 {@link ReportGenerationListener} 가 하고 이 서비스는 두 메서드를
 * <b>외부에서</b> 호출받는다(self-invocation 이면 @Transactional 프록시가 적용되지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ReportRepository reportRepository;

    /**
     * 보고서를 PROCESSING 으로 전이하고, 에이전트 호출 대상을 계산한다.
     *
     * @return 호출 대상. 보고서가 없으면 {@code null}(경쟁 삭제 등).
     */
    @Transactional
    public ReportGenerationTarget markProcessing(Long reportId) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return null; // 생성요청과 파이프라인 사이에 삭제된 경우 — 조용히 종료
        }
        report.markProcessing();
        return new ReportGenerationTarget(
                report.getReportType().agentType(),
                resolveEventId(report),
                formatDate(report.getPeriodStart()),
                formatDate(report.getPeriodEnd()));
    }

    /** 에이전트가 회신한 제목·본문을 적재하고 GENERATED 로 완료 처리한다(BE 가 직접 RDS 에 쓴다). */
    @Transactional
    public void applyGenerated(Long reportId, String title, String context) {
        reportRepository.findById(reportId)
                .ifPresent(report -> report.complete(title, context)); // 관리 엔티티 — dirty checking 으로 flush
    }

    /**
     * 에이전트 event_id 매핑. 유형별로 가리키는 대상(PK)이 다르다(에이전트 계약):
     * operation=turbine_id, farm_operation=wind_farm_id, defect=report_id, anomaly=anomaly_event_id.
     */
    private long resolveEventId(Report report) {
        return switch (report.getReportType()) {
            case TURBINE_OPERATION -> report.getTurbineId();
            case WIND_FARM_OPERATION -> report.getWindFarmId();
            case DEFECT_DIAGNOSIS -> report.getId();
            case ANOMALY_EVENT -> report.getAnomalyEventId() == null ? 0L : report.getAnomalyEventId();
        };
    }

    /** LocalDateTime → 에이전트가 받는 {@code YYYY-MM-DD}. 기간이 없으면 null(없는 값을 지어내지 않는다). */
    private static String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate().toString();
    }
}
