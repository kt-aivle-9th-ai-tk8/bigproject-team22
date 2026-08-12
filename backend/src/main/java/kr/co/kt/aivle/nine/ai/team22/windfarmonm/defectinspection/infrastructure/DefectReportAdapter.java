package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.DefectReportPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.ReportCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link DefectReportPort} 어댑터. maintenancereporting BC 의 application 서비스로만 위임한다.
 */
@Component
@RequiredArgsConstructor
public class DefectReportAdapter implements DefectReportPort {

    private final ReportCommandService reportCommandService;

    @Override
    public Long createDefectReport(Long windFarmId, LocalDateTime periodStart, LocalDateTime periodEnd,
                                   Long createdBy, String context) {
        return reportCommandService.createDefectDiagnosis(windFarmId, periodStart, periodEnd, createdBy, context);
    }
}
