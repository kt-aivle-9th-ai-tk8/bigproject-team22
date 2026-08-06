package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port.SessionManager;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    SessionManager sessionManager;
    @InjectMocks
    AuthService authService;

    private User user() {
        return User.create("E1001", "hashed", "홍길동", "010-1234-5678", Role.MANAGER);
    }

    @Test
    @DisplayName("로그인 성공 시 실패 카운트를 초기화한다")
    void login_success() {
        User user = user();
        user.increaseLoginFailCount();
        user.increaseLoginFailCount();
        when(userRepository.findByEmployeeId("E1001")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);

        LoginResult result = authService.login(new LoginCommand("E1001", "pw"));

        assertThat(result.employeeId()).isEqualTo("E1001");
        assertThat(result.role()).isEqualTo(Role.MANAGER);
        assertThat(user.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("실패 임계에 도달하면 계정이 정지되어 이후 로그인이 차단된다")
    void login_lockedByFailureThreshold() {
        User user = user();
        when(userRepository.findByEmployeeId("E1001")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        for (int i = 0; i < User.MAX_LOGIN_FAIL_COUNT; i++) {
            assertThatThrownBy(() -> authService.login(new LoginCommand("E1001", "wrong")))
                    .isInstanceOf(BusinessException.class);
        }

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        // 실패 횟수는 감사 목적으로 잠긴 뒤에도 남는다
        assertThat(user.getLoginFailCount()).isEqualTo(User.MAX_LOGIN_FAIL_COUNT);
        assertThatThrownBy(() -> authService.login(new LoginCommand("E1001", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("관리자가 정지시킨 계정도 같은 코드(A003)로 차단된다 — 자동/수동 잠금을 구분하지 않는다")
    void login_suspendedByAdmin() {
        User user = user();
        user.changeStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmployeeId("E1001")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginCommand("E1001", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(user.getLoginFailCount()).isZero();
        verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("비밀번호 불일치 시 실패 카운트를 누적하고 INVALID_CREDENTIALS 를 던진다")
    void login_wrongPassword() {
        User user = user();
        when(userRepository.findByEmployeeId("E1001")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("E1001", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(user.getLoginFailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 사번은 INVALID_CREDENTIALS 를 던진다(계정 열거 방지)")
    void login_nonexistent() {
        when(userRepository.findByEmployeeId("E404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("E404", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("1인 1세션: 이전 세션이 있으면 축출하고 새 세션을 등록한다")
    void registerSession_evictsPrevious() {
        User user = user();
        user.updateLatestSessionId("old-session");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.registerSession(1L, "new-session");

        verify(sessionManager).invalidate("old-session");
        assertThat(user.getLatestSessionId()).isEqualTo("new-session");
    }

    @Test
    @DisplayName("이전 세션이 없으면 축출을 시도하지 않는다")
    void registerSession_noPreviousSession() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.registerSession(1L, "new-session");

        verify(sessionManager, never()).invalidate(org.mockito.ArgumentMatchers.any());
        assertThat(user.getLatestSessionId()).isEqualTo("new-session");
    }
}
