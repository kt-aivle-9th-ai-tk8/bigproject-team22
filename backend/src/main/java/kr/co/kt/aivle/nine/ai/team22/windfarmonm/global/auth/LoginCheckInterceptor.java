package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증이 필요한 엔드포인트 앞단에서 세션 로그인 여부를 검사한다.
 * 공개 엔드포인트(회원가입/로그인 등)는 {@code WebConfig} 에서 제외 경로로 등록한다.
 * 승인 대기(GUEST) 계정은 로그인은 되지만 보호 경로 접근은 차단한다.
 */
@Component
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attribute = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_MEMBER);
        if (!(attribute instanceof LoginMember loginMember)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (loginMember.role() == Role.GUEST) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING);
        }
        return true;
    }
}
