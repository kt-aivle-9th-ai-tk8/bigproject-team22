package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.AuthService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.SessionConst;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.LoginRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증(로그인/로그아웃) API. context-path(/api) 기준 경로.
 * 관리자에 의한 타 사용자 강제 로그아웃은 admin 도메인으로 이관되었다.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인: POST /api/auth/login
     * 검증 성공 시 새 세션을 만들고 로그인 정보를 저장한다(Spring Session 이 Redis 에 저장하고 쿠키를 발급).
     * 이어서 1인 1세션 정책에 따라 이전 활성 세션을 축출하고 현재 세션을 등록한다.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request.toCommand());

        // 세션 고정 공격 방지: 같은 브라우저의 기존 세션이 있으면 파기 후 새로 발급
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionConst.LOGIN_MEMBER,
                new LoginMember(result.userId(), result.employeeId(), result.userName(), result.role()));

        // 1인 1세션: 다른 기기의 이전 세션 축출 + 현재 세션 id 등록
        authService.registerSession(result.userId(), session.getId());

        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", LoginResponse.from(result)));
    }

    /**
     * 로그아웃: POST /api/auth/logout
     * 현재 세션을 파기하고 쿠키를 만료시킨다.
     * RDB 의 latestSessionId 는 지우지 않는다(활성 여부의 근거가 아니며, 다음 로그인 때 덮어쓴다).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다.", null));
    }
}
