package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 경로(/admin/**) 전용 인가 인터셉터. ADMIN 권한이 아니면 차단한다.
 * 로그인·GUEST 여부는 {@link LoginCheckInterceptor}(선행)가 이미 검사한다.
 */
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attribute = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_MEMBER);
        if (!(attribute instanceof LoginMember loginMember)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (loginMember.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return true;
    }
}
