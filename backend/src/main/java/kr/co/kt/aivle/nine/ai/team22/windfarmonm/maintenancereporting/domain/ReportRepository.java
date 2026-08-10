package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;

import java.util.List;
import java.util.Optional;

/**
 * 보고서 저장소 포트.
 */
public interface ReportRepository {

    Report save(Report report);

    Optional<Report> findById(Long reportId);

    void delete(Report report);

    /**
     * 조건에 맞는 보고서를 최신순으로 조회한다. 본문은 담지 않는다(목록 전용 프로젝션).
     * <p>
     * {@code windFarmIds} 가 null 이면 단지 제한 없음(ADMIN)을 뜻한다. 빈 목록이면 담당 단지가 없다는 뜻이므로
     * 결과도 비어야 한다 — 이 둘을 뭉개면 담당 없는 사용자가 전체를 보게 되므로 호출측이 구분해 넘겨야 한다.
     * 나머지 인자는 null 이면 해당 조건을 적용하지 않는다.
     */
    List<ReportSummaryResult> search(List<Long> windFarmIds, Long windFarmId, Long turbineId, ReportType reportType);
}
