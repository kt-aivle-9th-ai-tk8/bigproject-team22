package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 계열 응답(가입·로그인)에 <b>본인 정보라도</b> 평문 개인정보가 실리지 않는지 고정한다.
 * <p>
 * "본인 것이니 예외"라는 판단은 화면 앞의 사람이 계정 주인일 때만 성립한다. FE 가 로그인 응답을
 * {@code localStorage.userInfo} 로 오래 보관하므로, 단말·세션이 탈취되면 그 값이 "이 계정이 누구
 * 것인지"를 알려주는 단서가 된다. 사번은 로그인 ID 이기도 하다.
 */
class AuthResponseMaskingTest {

    @Test
    @DisplayName("가입 응답의 사번·이름은 마스킹된다")
    void signUpResponse_isMasked() {
        UserResponse response = UserResponse.from(new UserResult(1L, "2401001", "홍길동", Role.GUEST));

        assertThat(response.employeeId()).isEqualTo("24***01");
        assertThat(response.userName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("로그인 응답은 본인 정보라도 마스킹된다")
    void loginResponse_isMaskedEvenThoughItIsOwnData() {
        LoginResponse response = LoginResponse.from(new LoginResult(1L, "2401001", "홍길동", Role.MANAGER));

        assertThat(response.employeeId()).isEqualTo("24***01");
        assertThat(response.userName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("로그인 응답 어디에도 원문이 남지 않는다")
    void loginResponse_leaksNoRawValue() {
        LoginResponse response = LoginResponse.from(new LoginResult(1L, "2401001", "홍길동", Role.MANAGER));

        assertThat(response.toString()).doesNotContain("2401001").doesNotContain("홍길동");
    }

    @Test
    @DisplayName("role 은 마스킹 대상이 아니다 — FE 가 화면 분기에 쓴다")
    void role_isNotMasked() {
        LoginResponse response = LoginResponse.from(new LoginResult(1L, "2401001", "홍길동", Role.MANAGER));

        assertThat(response.role()).isEqualTo(Role.MANAGER);
    }
}
