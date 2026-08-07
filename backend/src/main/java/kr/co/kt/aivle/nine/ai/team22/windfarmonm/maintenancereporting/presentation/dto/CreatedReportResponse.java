package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto;

/**
 * 생성 접수 응답. 본문 생성은 뒤에서 이어지므로 <b>식별자만</b> 돌려준다(202 Accepted).
 * 클라이언트는 이 id 로 이후 상태를 조회한다.
 */
public record CreatedReportResponse(
        String id
) {
    public static CreatedReportResponse of(Long reportId) {
        return new CreatedReportResponse(String.valueOf(reportId));
    }
}
