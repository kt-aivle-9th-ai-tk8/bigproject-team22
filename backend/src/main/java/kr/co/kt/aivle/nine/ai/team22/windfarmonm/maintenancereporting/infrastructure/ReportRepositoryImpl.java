package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportJpaRepository jpaRepository;

    @Override
    public Report save(Report report) {
        return jpaRepository.save(report);
    }

    @Override
    public Optional<Report> findById(Long reportId) {
        return jpaRepository.findById(reportId);
    }

    @Override
    public void delete(Report report) {
        jpaRepository.delete(report);
    }

    @Override
    public List<ReportSummaryResult> search(List<Long> windFarmIds, Long windFarmId, Long turbineId, ReportType reportType) {
        if (windFarmIds == null) { // 제한 없음(ADMIN)
            return jpaRepository.search(windFarmId, turbineId, reportType);
        }
        if (windFarmIds.isEmpty()) { // 담당 단지가 없으면 볼 수 있는 보고서도 없다(빈 IN 절을 만들지 않는다)
            return List.of();
        }
        return jpaRepository.searchWithin(windFarmIds, windFarmId, turbineId, reportType);
    }
}
