package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

/**
 * report-agent 에 넘길 생성 대상. 에이전트 계약이 {@code (report_type, event_id)} 하나로 4개 유형을 공용하므로,
 * {@code eventId} 가 가리키는 대상은 유형마다 다르다(operation=터빈 번호, farm_operation=단지 id,
 * defect=report_id, anomaly=event_id). 매핑은 {@code ReportGenerationService} 가 수행한다.
 *
 * @param agentType 에이전트 쪽 유형 문자열(소문자). {@code ReportType.agentType()}
 * @param eventId   유형별 대상 식별자(에이전트 계약이 int)
 */
public record ReportGenerationTarget(String agentType, int eventId) {
}
