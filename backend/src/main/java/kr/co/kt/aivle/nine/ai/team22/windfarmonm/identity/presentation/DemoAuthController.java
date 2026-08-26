package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.SessionConst;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config.DemoProperties;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.DemoLoginService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 데모(읽기 전용) 진입: {@code GET /api/auth/demo}
 * <p>
 * 로그인 없이 링크 한 번으로 데모 계정 세션을 발급하고 FE 데모 화면으로 리다이렉트한다.
 * 인증 우회 경로이므로 다음 안전장치를 둔다:
 * <ul>
 *   <li>{@code demo.enabled=false}(기본) 인 환경에서는 404 로 숨긴다.</li>
 *   <li>세션 role 은 항상 {@code Role.DEMO}({@code DemoLoginService}) → 쓰기 차단 + {@code /admin/**}(개인정보) 차단.</li>
 *   <li>쓰기(비 GET) 요청은 {@code DemoReadOnlyInterceptor} 가 403 으로 막는다 → Read-Only 보장.</li>
 *   <li>{@code registerSession} 을 호출하지 않아 1인 1세션 축출에서 제외 → 동시 접속 허용.</li>
 * </ul>
 * 공개 경로 등록은 {@code WebConfig#PUBLIC_PATHS} 에서 한다.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DemoAuthController {

    private final DemoProperties demoProperties;
    private final DemoLoginService demoLoginService;

    @GetMapping("/demo")
    public ResponseEntity<?> enterDemo(HttpServletRequest httpRequest) {
        if (!demoProperties.enabled()) {
            // 데모가 비활성인 환경에서는 엔드포인트의 존재 자체를 숨긴다.
            return ResponseEntity.notFound().build();
        }

        LoginResult result = demoLoginService.resolveDemoMember();

        // 세션 고정 공격 방지: 기존 세션이 있으면 파기 후 새로 발급(로그인 경로와 동일한 관례).
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        // role=DEMO 인 주체를 심는다. 읽기 전용 여부는 이 role 하나로 판정된다(별도 플래그 없음).
        session.setAttribute(SessionConst.LOGIN_MEMBER,
                new LoginMember(result.userId(), result.employeeId(), result.userName(), result.role()));
        // registerSession 미호출 — 데모 세션은 1인 1세션 축출 대상이 아니라 동시 접속을 허용한다.
        // 감사 LOGIN 도 남기지 않는다(실제 로그인이 아니다).

        String redirectUrl = demoProperties.redirectUrl();
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
        }
        return ResponseEntity.ok(ApiResponse.success("데모 세션이 시작되었습니다.", null));
    }
}
