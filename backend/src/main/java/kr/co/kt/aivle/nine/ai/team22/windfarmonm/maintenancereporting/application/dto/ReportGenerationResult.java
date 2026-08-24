package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

/**
 * report-agent 생성 결과. 에이전트가 제목과 본문을 <b>분리</b>해 돌려준다(제목은 BE 가 만들지 않는다).
 * <p>
 * 에이전트는 재시도 소진 후 "부적합"이어도 본문을 담아 HTTP 200 을 줄 수 있으므로, 채택 여부는 상태코드가
 * 아니라 {@code found} + {@code context} 유무로 판단한다. {@code verdict} 는 로깅용.
 *
 * @param found   대상을 찾아 생성을 시도했는지(대상없음이면 false)
 * @param title   보고서 제목(Report.title 저장용, 200자 이내). 없으면 null
 * @param context 보고서 본문 마크다운(제목 H1 제외, Report.context 저장용). 없으면 null
 * @param verdict 에이전트 자체 판정(적합/부적합 등). 채택 판단엔 쓰지 않고 로깅에만 쓴다.
 */
public record ReportGenerationResult(boolean found, String title, String context, String verdict) {

    /** 대상 없음/생성 실패(호출은 했으나 본문 없음). */
    public static ReportGenerationResult notGenerated() {
        return new ReportGenerationResult(false, null, null, null);
    }
}
