package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 보고서 조회.
 * <p>
 * 열람 권한이 없는 보고서는 <b>존재하지 않는 것과 동일하게</b> {@link ErrorCode#REPORT_NOT_FOUND} 로 응답한다.
 * 403 과 404 를 구분하면 담당이 아닌 사용자에게 "그 보고서는 있다"는 사실이 드러나기 때문이다.
 * 두 경우의 응답이 같으므로 다른 자원과 달리 검사 순서(인가→존재)를 강제할 필요가 없다.
 */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ReportRepository reportRepository;
    private final ReportAssetPort assetPort;

    /** 목록 조회. 선택 조건은 null 이면 미적용. 페이징은 두지 않는다(MVP 계약). */
    @Transactional(readOnly = true)
    public List<ReportSummaryResult> getReports(Long userId, boolean admin,
                                                Long windFarmId, Long turbineId, ReportType reportType) {
        List<Long> viewable = assetPort.viewableWindFarmIds(userId, admin); // ADMIN 이면 null(제한 없음)
        return reportRepository.search(viewable, windFarmId, turbineId, reportType);
    }

    /** 단건 조회. 열람 범위 밖이면 404. */
    @Transactional(readOnly = true)
    public ReportResult getReport(Long userId, boolean admin, Long reportId) {
        return ReportResult.from(readViewable(userId, admin, reportId));
    }

    /**
     * 열람 가능한 보고서를 읽는다. 없거나 범위 밖이면 {@link ErrorCode#REPORT_NOT_FOUND}.
     * 수정/삭제도 같은 판정을 쓰므로 패키지 내부에 공개한다.
     */
    Report readViewable(Long userId, boolean admin, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        List<Long> viewable = assetPort.viewableWindFarmIds(userId, admin);
        if (viewable != null && !viewable.contains(report.getWindFarmId())) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }
}
