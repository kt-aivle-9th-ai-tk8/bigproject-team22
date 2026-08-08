package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;

/**
 * 보고서 본문 생성을 외부 report-agent 에 위임하는 포트. 소비자(maintenancereporting)가 소유한다.
 * <p>
 * <b>동기 요청-응답</b>이다 — 에이전트를 호출하면 그 자리에서 생성 본문(draft)을 돌려받는다(현재 에이전트가
 * 웹훅 콜백을 지원하지 않기 때문). 호출이 길 수 있으므로 호출측은 요청 스레드가 아니라 별도 executor 에서 부른다.
 */
public interface ReportGenerationPort {

    /**
     * 에이전트가 설정되어 호출 가능한 상태인지. base-url 미설정이면 false 이며, 이때 호출측은
     * 생성을 건너뛰고 보고서를 PENDING 으로 남긴다(앱 기동·생성요청은 정상, 본문만 나중에).
     */
    boolean isEnabled();

    /**
     * 대상에 대한 보고서 본문을 <b>동기로</b> 생성 요청한다. 실패/대상없음은 예외가 아니라
     * {@link ReportGenerationResult#notGenerated()} 로 돌려준다(호출측이 보고서를 PROCESSING 으로 남기고 로깅).
     */
    ReportGenerationResult generate(ReportGenerationTarget target);
}
