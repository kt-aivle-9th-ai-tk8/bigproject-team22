package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;

import java.time.LocalDateTime;

/**
 * 보고서 목록 항목.
 * <p>
 * 본문({@code context})을 <b>일부러 담지 않는다.</b> 목록 응답에 본문이 나가지 않는데도 엔티티를 통째로 읽으면
 * 모든 행의 마크다운 전문을 읽어 버리고 버리게 된다. 페이징이 없어 한 번에 전 행을 훑으므로 그 낭비가 그대로
 * 누적된다. 그래서 조회 단계에서부터 필요한 컬럼만 가져오는 프로젝션으로 쓴다.
 */
public record ReportSummaryResult(
        Long id,
        Long windFarmId,
        Long turbineId,
        ReportType reportType,
        String title,
        ReportStatus status,
        LocalDateTime generatedAt
) {
}
