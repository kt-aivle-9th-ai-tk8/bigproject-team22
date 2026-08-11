package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

/**
 * report-agent 에 넘길 생성 대상. 에이전트 계약이 {@code (report_type, event_id, period_start, period_end)} 로
 * 4개 유형을 공용하므로, {@code eventId} 가 가리키는 대상은 유형마다 다르다(operation=turbine_id,
 * farm_operation=wind_farm_id, defect=report_id, anomaly=anomaly_event_id). 매핑은 {@code ReportGenerationService} 가 한다.
 * <p>
 * 기간은 operation/farm_operation 에만 적용된다(anomaly/defect 는 에이전트가 무시). 보고서에 기간이 있으면
 * 그대로 전달하고, 없으면 null 로 둔다 — 없는 기간을 지어내지 않는다.
 *
 * @param agentType   에이전트 쪽 유형 문자열(소문자). {@code ReportType.agentType()}
 * @param eventId     유형별 대상 식별자(PK). 큰 id 도 담도록 long
 * @param periodStart 조회 시작일(YYYY-MM-DD) 또는 null
 * @param periodEnd   조회 종료일(YYYY-MM-DD, 해당일 포함) 또는 null
 */
public record ReportGenerationTarget(String agentType, long eventId, String periodStart, String periodEnd) {
}
