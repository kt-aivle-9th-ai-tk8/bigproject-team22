package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.event;

/**
 * 보고서 생성이 요청되었음을 알리는 도메인 이벤트.
 * <p>
 * {@code ReportCommandService.create()} 가 행을 저장한 뒤 발행하고,
 * {@code ReportGenerationListener} 가 <b>커밋 이후</b>({@code AFTER_COMMIT})에 받아 생성 파이프라인을 돈다.
 * 커밋 전에 받으면 리스너가 아직 없는 행을 읽어 실패하므로 반드시 커밋 이후여야 한다.
 */
public record ReportGenerationRequested(Long reportId) {
}
