package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

/**
 * 보고서 대상(발전소/터빈)의 표시·식별 정보. 제목 조립과 에이전트 event_id 매핑에 쓴다.
 * assetmanagement 에서 <b>인가 없이</b> 조회한 값이다(호출측이 이미 담당 인가를 통과한 뒤 부른다).
 *
 * @param windFarmName 단지명(없으면 null)
 * @param turbineCode  터빈 코드(예: {@code U2}). 단지 단위 보고서는 null
 */
public record ReportTargetInfo(String windFarmName, String turbineCode) {
}
