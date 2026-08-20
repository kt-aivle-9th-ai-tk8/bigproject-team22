package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.AuthService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.SessionConst;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditAction;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.LoginRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증(로그인/로그아웃) API. context-path(/api) 기준 경로.
 * 관리자에 의한 타 사용자 강제 로그아웃은 admin 도메인으로 이관되었다.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

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
        // 세션 생성
        HttpSession session = httpRequest.getSession(true);

        try {
            // 현재 세션을 최기 세션으로 등록
            authService.registerSession(result.userId(), session.getId());
            // 현재 세션에 권한 부여
            session.setAttribute(SessionConst.LOGIN_MEMBER,
                    new LoginMember(result.userId(), result.employeeId(), result.userName(), result.role()));
        } catch (RuntimeException e) {
            // 세션 등록 과정에서 문제 발생 시 세션 무효화 처리
            session.invalidate();
            throw e;
        }

        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", LoginResponse.from(result)));
    }

    /**
     * 로그아웃: POST /api/auth/logout
     * 현재 세션을 파기하고 쿠키를 만료시킨다.
     * RDB 의 latestSessionId 는 지우지 않는다(활성 여부의 근거가 아니며, 다음 로그인 때 덮어쓴다).
     * <p>
     * <b>세션 파기가 감사 기록보다 우선한다.</b> 다른 감사 지점은 기록이 실패하면 업무 처리도 되돌리지만
     * (기록 없이 처리된 개인정보를 만들지 않기 위해), 로그아웃만은 반대다 — 기록에 실패했다고 세션을
     * 살려 두면 사용자가 로그아웃했다고 믿는 계정이 그대로 열린 채 남는다. 기록 누락보다 나쁜 결과다.
     * 그래서 파기는 {@code finally} 에서 하고, 기록 실패는 ERROR 로그로만 남긴 뒤 200 을 준다
     * (세션은 실제로 파기됐으므로 실패라고 답하는 편이 오히려 거짓이다 — 누락은 로그로 점검한다).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            // 주체를 파기 전에 꺼내 이벤트에 직접 싣는다. 수신측이 세션을 다시 읽지 않게 되어
            // 기록과 파기의 순서에 더는 얽매이지 않는다.
            Object attribute = session.getAttribute(SessionConst.LOGIN_MEMBER);
            Long userId = attribute instanceof LoginMember member ? member.userId() : null;
            try {
                if (userId != null) { // 주체를 모르면 남길 수 없다(user_id 는 NOT NULL)
                    eventPublisher.publishEvent(AuditEvent.by(userId, AuditAction.LOGOUT, null, null));
                }
            } catch (RuntimeException e) {
                log.error("로그아웃 감사 기록에 실패했다 userId={} — 세션은 예정대로 파기한다", userId, e);
            } finally {
                session.invalidate();
            }
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다.", null));
    }
}
