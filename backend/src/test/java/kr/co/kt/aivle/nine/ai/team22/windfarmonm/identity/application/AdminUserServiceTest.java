package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port.SessionManager;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    SessionManager sessionManager;
    @InjectMocks
    AdminUserService adminUserService;

    private User userWithSession(String sessionId) {
        User user = User.create("E1001", "hashed", "홍길동", "010-1234-5678", Role.MANAGER);
        if (sessionId != null) {
            user.updateLatestSessionId(sessionId);
        }
        return user;
    }

    @Test
    @DisplayName("활성 여부는 RDB 컬럼이 아니라 세션 저장소(exists)로 판정한다")
    void getUsers_activenessFromSessionStore() {
        User active = userWithSession("live-session");
        User staleColumn = userWithSession("dead-session"); // 컬럼엔 값이 있으나 Redis 엔 없음
        when(userRepository.findAll()).thenReturn(List.of(active, staleColumn));
        when(sessionManager.exists("live-session")).thenReturn(true);
        when(sessionManager.exists("dead-session")).thenReturn(false); // TTL 만료 반영

        List<AdminUserResult> results = adminUserService.getUsers();

        assertThat(results).extracting(AdminUserResult::sessionActive)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("권한 변경 시 권한을 갱신하고 기존 세션을 강제 종료한다")
    void changeRole_updatesAndInvalidatesSession() {
        User user = userWithSession("live-session");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserResult result = adminUserService.changeRole(1L, Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.sessionActive()).isFalse();
        verify(sessionManager).invalidate("live-session");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 권한 변경은 USER_NOT_FOUND")
    void changeRole_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.changeRole(99L, Role.ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("강제 로그아웃 시 활성 세션이 있으면 파기한다")
    void forceLogout_invalidatesSession() {
        User user = userWithSession("live-session");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminUserService.forceLogout(1L);

        verify(sessionManager).invalidate("live-session");
    }

    @Test
    @DisplayName("세션 포인터가 없으면 강제 로그아웃은 아무 것도 하지 않는다")
    void forceLogout_noSession() {
        User user = userWithSession(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminUserService.forceLogout(1L);

        verify(sessionManager, never()).invalidate(any());
    }
}
