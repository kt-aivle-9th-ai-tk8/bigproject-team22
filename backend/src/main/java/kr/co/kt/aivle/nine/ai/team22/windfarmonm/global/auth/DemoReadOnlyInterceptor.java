package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 데모(읽기 전용) 세션이 <b>상태를 바꾸는 요청</b>을 보내면 차단한다.
 * 판정 기준은 세션 주체의 역할이다 — {@code LoginMember.isDemo()}({@code Role.DEMO}).
 * <p>
 * "데모면 안전 메서드(GET/HEAD/OPTIONS)만 허용"이라는 규칙 하나로 Read-Only 를 보장할 수 있는 근거:
 * 이 서비스의 쓰기는 전부 POST/PUT/PATCH/DELETE 이고 조회는 전부 GET 이다(조회를 POST 로 받는
 * 엔드포인트가 없다). 따라서 현재는 물론 <b>앞으로 추가될 모든 쓰기 엔드포인트까지</b> 자동으로 막힌다.
 * OPTIONS(CORS 프리플라이트)와 HEAD(읽기)는 상태를 바꾸지 않으므로 허용한다 — OPTIONS 를 막으면
 * 데모 FE 가 교차 출처 GET 조차 하지 못한다.
 * <p>
 * 읽기 전용 보장은 {@code Role.DEMO} 자체의 성질이므로 이 인터셉터는 {@code demo.enabled} 와 무관하게
 * 항상 등록한다(데모가 꺼져 있어도 어떤 경로로든 DEMO 주체가 생기면 쓰기는 막힌다). 등록·경로는
 * {@code WebConfig} 가 담당한다.
 */
@Component
public class DemoReadOnlyInterceptor implements HandlerInterceptor {

    /** 상태를 바꾸지 않는 안전한 HTTP 메서드. 데모 세션은 이것만 허용된다. */
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attribute = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_MEMBER);
        if (attribute instanceof LoginMember member
                && member.isDemo()
                && !READ_ONLY_METHODS.contains(request.getMethod())) {
            throw new BusinessException(ErrorCode.DEMO_READ_ONLY);
        }
        return true;
    }
}
