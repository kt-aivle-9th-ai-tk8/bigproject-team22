package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 목록 조회는 <b>본문을 제외한 프로젝션</b>으로 읽는다. 엔티티를 통째로 읽으면 응답에 싣지도 않을 마크다운
 * 전문을 전 행에 대해 가져오게 되고, 페이징이 없어 그 낭비가 누적된다.
 * <p>
 * 열람 범위(단지 제한)의 유무에 따라 쿼리를 나눈 이유: 컬렉션 파라미터에 null 을 바인딩하는 동작은 구현체마다
 * 다르고 IN 절 생성이 깨질 수 있다. 분기를 SQL 이 아니라 호출측에 두는 편이 안전하다. 나머지 선택 조건은
 * 스칼라라 {@code :p is null} 패턴이 문제없이 동작한다.
 */
public interface ReportJpaRepository extends JpaRepository<Report, Long> {

    String SUMMARY_SELECT = """
            select new kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult(
                       r.id, r.windFarmId, r.turbineId, r.reportType, r.title, r.status, r.generatedAt)
            from Report r
            """;

    String FILTERS = """
            (:windFarmId is null or r.windFarmId = :windFarmId)
              and (:turbineId is null or r.turbineId = :turbineId)
              and (:reportType is null or r.reportType = :reportType)
            order by r.generatedAt desc, r.id desc
            """;

    /** 단지 제한 없이 조회(ADMIN). */
    @Query(SUMMARY_SELECT + " where " + FILTERS)
    List<ReportSummaryResult> search(@Param("windFarmId") Long windFarmId,
                                     @Param("turbineId") Long turbineId,
                                     @Param("reportType") ReportType reportType);

    /** 담당 단지로 범위를 좁혀 조회. */
    @Query(SUMMARY_SELECT + " where r.windFarmId in :windFarmIds and " + FILTERS)
    List<ReportSummaryResult> searchWithin(@Param("windFarmIds") List<Long> windFarmIds,
                                           @Param("windFarmId") Long windFarmId,
                                           @Param("turbineId") Long turbineId,
                                           @Param("reportType") ReportType reportType);
}
