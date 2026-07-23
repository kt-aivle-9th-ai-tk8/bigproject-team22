package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User newUser() {
        return User.create("E1001", "hashed", "홍길동", Role.GUEST);
    }

    @Test
    @DisplayName("생성 시 실패 카운트 0, 지정한 권한으로 초기화된다")
    void create_initializesDefaults() {
        User user = User.create("E1001", "hashed", "홍길동", Role.GUEST);

        assertThat(user.getRole()).isEqualTo(Role.GUEST);
        assertThat(user.getLoginFailCount()).isZero();
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
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("실패 카운트가 임계치에 도달하면 잠긴다")
    void isLocked_trueAtThreshold() {
        User user = newUser();
        for (int i = 0; i < User.MAX_LOGIN_FAIL_COUNT; i++) {
            user.increaseLoginFailCount();
        }
        assertThat(user.isLocked()).isTrue();
    }

    @Test
    @DisplayName("실패 카운트를 초기화하면 잠금이 풀린다")
    void resetLoginFailCount() {
        User user = newUser();
        for (int i = 0; i < User.MAX_LOGIN_FAIL_COUNT; i++) {
            user.increaseLoginFailCount();
        }

        user.resetLoginFailCount();

        assertThat(user.getLoginFailCount()).isZero();
        assertThat(user.isLocked()).isFalse();
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
