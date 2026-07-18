package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.AuthService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.SessionConst;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.auth.dto.LoginRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.auth.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증(로그인/로그아웃/강제 로그아웃) API. context-path(/api) 기준 경로.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인: POST /api/auth/login
     * 검증 성공 시 새 세션을 만들고 로그인 정보를 저장한다(Spring Session 이 Redis 에 저장하고 쿠키를 발급).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request.toCommand());

        // 세션 고정 공격 방지: 기존 세션이 있으면 파기 후 새로 발급
        HttpSession oldSession = httpRequest.getSession(false); // get prev session
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true); // create new session
        session.setAttribute(SessionConst.LOGIN_MEMBER,
                new LoginMember(result.userId(), result.employeeId(), result.userName(), result.role()));

        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", LoginResponse.from(result)));
    }

    /**
     * 로그아웃: POST /api/auth/logout
     * 현재 세션을 파기하고 쿠키를 만료시킨다.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다.", null));
    }

    /**
     * 강제 로그아웃: DELETE /api/auth/sessions/{sessionId}
     * 지정한 세션을 강제로 파기한다.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable String sessionId) {
        authService.forceLogout(sessionId);
        return ResponseEntity.ok(ApiResponse.success("세션이 강제 종료되었습니다.", null));
    }
}
