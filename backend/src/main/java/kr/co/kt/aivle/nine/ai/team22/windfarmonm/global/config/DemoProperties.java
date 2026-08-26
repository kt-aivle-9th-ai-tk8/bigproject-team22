package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 데모(읽기 전용) 접근 설정. application yaml 의 {@code demo.*} 로 주입된다.
 * <p>
 * 데모는 로그인 없이 링크 한 번으로 진입하는 기능이라 <b>인증 우회 경로</b>다. 그래서 다음을 지킨다:
 * <ul>
 *   <li>{@code enabled} 기본값 <b>false</b> — 설정하지 않은 환경(운영·CI)에서는 엔드포인트가 404 로 숨는다.</li>
 *   <li>전 필드에 기본값이 있어, 운영 프로파일이 필수로 요구하는 환경변수를 늘리지 않는다
 *       ({@code ProdEnvironmentContractTest} 통과).</li>
 *   <li>데모 신원은 기존 계정({@code employeeId})을 재사용하되, 세션 role 은 항상 MANAGER 로 스코프해
 *       회원관리({@code /admin/**}) 등 개인정보 노출 경로를 원천 차단한다({@code DemoLoginService}).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(

        /** 데모 진입 활성화 여부. false 면 {@code /auth/demo} 는 404, 읽기전용 인터셉터·초기화 빈도 미등록. */
        @DefaultValue("false") boolean enabled,

        /** 데모 신원으로 사용할 기존 계정의 사번. 이 계정으로 세션을 발급한다(MANAGER 로 스코프). */
        @DefaultValue("a123456") String employeeId,

        /** 데모 계정이 담당(열람)할 단지 id. 시작 시 이 배정을 보장한다(1=장흥, 2=화순). */
        @DefaultValue({"1", "2"}) List<Long> windFarmIds,

        /** 세션 발급 후 이동할 FE 데모 화면 URL. 비어 있으면 리다이렉트 없이 200 을 반환한다. */
        String redirectUrl
) {
}
