package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User newUser() {
        return User.create("E1001", "hashed", "홍길동", "010-1234-5678", Role.GUEST);
    }

    private User lockedUser() {
        User user = newUser();
        for (int i = 0; i < User.MAX_LOGIN_FAIL_COUNT; i++) {
            user.increaseLoginFailCount();
        }
        return user;
    }

    @Test
    @DisplayName("생성 시 실패 카운트 0, ACTIVE, 지정한 권한으로 초기화된다")
    void create_initializesDefaults() {
        User user = User.create("E1001", "hashed", "홍길동", "010-1234-5678", Role.GUEST);

        assertThat(user.getRole()).isEqualTo(Role.GUEST);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLoginFailCount()).isZero();
        assertThat(user.getPhone()).isEqualTo("010-1234-5678");
        assertThat(user.getLatestSessionId()).isNull();
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("실패 카운트가 임계치 미만이면 잠기지 않는다")
    void isLocked_falseBelowThreshold() {
        User user = newUser();
        for (int i = 0; i < User.MAX_LOGIN_FAIL_COUNT - 1; i++) {
            user.increaseLoginFailCount();
        }
        assertThat(user.getLoginFailCount()).isEqualTo(User.MAX_LOGIN_FAIL_COUNT - 1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("실패 카운트가 임계치에 도달하면 계정이 SUSPENDED 로 전이된다")
    void isLocked_trueAtThreshold() {
        User user = lockedUser();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.isLocked()).isTrue();
    }

    @Test
    @DisplayName("실패 카운트만 초기화해서는 잠금이 풀리지 않는다(잠금 근거는 status 하나)")
    void resetLoginFailCount_doesNotUnlock() {
        User user = lockedUser();

        user.resetLoginFailCount();

        assertThat(user.getLoginFailCount()).isZero();
        assertThat(user.isLocked()).isTrue(); // status 는 그대로 SUSPENDED
    }

    @Test
    @DisplayName("활성화하면 잠금이 풀리고 실패 카운트도 함께 초기화된다(즉시 재잠금 방지)")
    void changeStatus_activateResetsFailCount() {
        User user = lockedUser();
        assertThat(user.getLoginFailCount()).isEqualTo(User.MAX_LOGIN_FAIL_COUNT);

        user.changeStatus(UserStatus.ACTIVE);

        assertThat(user.isLocked()).isFalse();
        assertThat(user.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("관리자가 실패 이력 없이도 계정을 정지시킬 수 있다")
    void changeStatus_suspendWithoutFailures() {
        User user = newUser();

        user.changeStatus(UserStatus.SUSPENDED);

        assertThat(user.isLocked()).isTrue();
        assertThat(user.getLoginFailCount()).isZero(); // 자동 잠금이 아니므로 카운트는 0 그대로
    }

    @Test
    @DisplayName("권한을 변경할 수 있다(관리자 승인)")
    void changeRole() {
        User user = newUser();

        user.changeRole(Role.MANAGER);

        assertThat(user.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    @DisplayName("최신 세션 id 를 갱신할 수 있다")
    void updateLatestSessionId() {
        User user = newUser();

        user.updateLatestSessionId("session-1");

        assertThat(user.getLatestSessionId()).isEqualTo("session-1");
    }
}
