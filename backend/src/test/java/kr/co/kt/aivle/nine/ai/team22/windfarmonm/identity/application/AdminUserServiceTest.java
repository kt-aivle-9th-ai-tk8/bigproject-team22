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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

    @Test
    @DisplayName("거절: 승인 대기(GUEST) 계정은 세션 파기 후 삭제한다")
    void rejectSignUp_deletesGuest() {
        User guest = User.create("E2001", "hashed", "신입", "010-0000-0000", Role.GUEST);
        guest.updateLatestSessionId("sess-1");
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(guest));

        adminUserService.rejectSignUp(9L);

        verify(sessionManager).invalidate("sess-1"); // 계정이 사라진 뒤 남은 세션은 주인 없는 접근이 된다
        verify(userRepository).delete(guest);
    }

    @Test
    @DisplayName("거절: 이미 승인된 계정은 400 U003 — 삭제는 되돌릴 수 없어 정지로 다뤄야 한다")
    void rejectSignUp_rejectsApproved() {
        User manager = User.create("E1001", "hashed", "홍길동", "010-1234-5678", Role.MANAGER);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> adminUserService.rejectSignUp(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_PENDING);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("거절: 남긴 데이터가 참조 중이면 409 U004 로 번역한다")
    void rejectSignUp_translatesFkViolation() {
        User guest = User.create("E2001", "hashed", "신입", "010-0000-0000", Role.GUEST);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(guest));
        doThrow(new DataIntegrityViolationException("fk"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> adminUserService.rejectSignUp(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_HAS_REFERENCES);
    }

    @Test
    @DisplayName("거절: 없는 사용자는 404 U002")
    void rejectSignUp_notFound() {
        when(userRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.rejectSignUp(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("거절: 역할 확인을 잠금 조회로 한다 — 잠금 없이 읽으면 확인과 삭제 사이에 승인이 끼어든다")
    void rejectSignUp_readsUnderLock() {
        User guest = User.create("E2001", "hashed", "신입", "010-0000-0000", Role.GUEST);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(guest));

        adminUserService.rejectSignUp(9L);

        verify(userRepository).findByIdForUpdate(9L);
        verify(userRepository, never()).findById(any());   // 비잠금 조회로 되돌아가면 경쟁이 복원된다
    }
}
