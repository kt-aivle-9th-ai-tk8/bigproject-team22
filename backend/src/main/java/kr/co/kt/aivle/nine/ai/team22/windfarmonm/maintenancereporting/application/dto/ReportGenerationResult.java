package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

/**
 * report-agent 생성 결과.
 * <p>
 * 에이전트는 재시도 소진 후 "부적합"이어도 HTTP 200 을 돌려주므로, 본문 채택 여부는 상태코드가 아니라
 * {@code found} + {@code draft} 유무로 판단한다. {@code verdict} 는 로깅용.
 *
 * @param found   대상을 찾아 생성을 시도했는지(에이전트 404 대상없음이면 false)
 * @param draft   생성된 마크다운 본문(없으면 null)
 * @param verdict 에이전트 자체 판정(적합/부적합 등). 채택 판단엔 쓰지 않고 로깅에만 쓴다.
 */
public record ReportGenerationResult(boolean found, String draft, String verdict) {

    /** 대상 없음/생성 실패(호출은 했으나 본문 없음). */
    public static ReportGenerationResult notGenerated() {
        return new ReportGenerationResult(false, null, null);
    }
}
