package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증이 필요한 엔드포인트 앞단에서 세션 로그인 여부를 검사한다.
 * 공개 엔드포인트(회원가입/로그인 등)는 {@code WebConfig} 에서 제외 경로로 등록한다.
 * <p>
 * 승인 대기(GUEST) 계정은 <b>로그인 자체가 차단</b>되므로({@code AuthService}, A004) 세션을 가질 수 없다.
 * 따라서 여기서 role 을 다시 검사하지 않는다 — 세션이 있다는 것은 이미 승인된 계정이라는 뜻이다.
 */
@Component
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attribute = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_MEMBER);
        if (!(attribute instanceof LoginMember)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }
}
