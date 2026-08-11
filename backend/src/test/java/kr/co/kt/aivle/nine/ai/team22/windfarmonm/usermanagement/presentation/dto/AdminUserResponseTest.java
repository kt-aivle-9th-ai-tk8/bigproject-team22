package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타인의 개인정보가 나가는 유일한 응답이므로, 사번·이름이 평문으로 새지 않는지 고정한다.
 * 동시에 FE 가 조작 키로 쓰는 {@code userId} 는 손대지 않는지도 함께 고정한다 —
 * 이걸 가리면 권한 변경·강제 로그아웃·단지 배정이 전부 깨진다.
 */
class AdminUserResponseTest {

    private static AdminUserDetail detail(Long userId, String employeeId, String userName) {
        return new AdminUserDetail(userId, employeeId, userName, Role.MANAGER, UserStatus.ACTIVE, false, List.of());
    }

    @Test
    @DisplayName("사번과 이름은 마스킹되어 나간다")
    void masksEmployeeIdAndUserName() {
        AdminUserResponse response = AdminUserResponse.from(detail(7L, "2401001", "홍길동"));

        assertThat(response.employeeId()).isEqualTo("24***01");
        assertThat(response.userName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("userId 는 마스킹하지 않는다 — FE 가 수정·강제로그아웃 요청에 쓰는 키다")
    void doesNotMaskUserId() {
        AdminUserResponse response = AdminUserResponse.from(detail(7L, "2401001", "홍길동"));

        assertThat(response.userId()).isEqualTo("7");
    }

    @Test
    @DisplayName("응답 어디에도 원문 사번·이름이 남지 않는다")
    void leaksNoRawValue() {
        AdminUserResponse response = AdminUserResponse.from(detail(7L, "2401001", "홍길동"));

        assertThat(response.toString()).doesNotContain("2401001").doesNotContain("홍길동");
    }

    @Test
    @DisplayName("목록 응답의 모든 항목이 마스킹된다")
    void masksEveryItemInList() {
        AdminUsersResponse response = AdminUsersResponse.from(List.of(
                detail(1L, "2401001", "홍길동"),
                detail(2L, "2401002", "김민수")));

        assertThat(response.users()).extracting(AdminUserResponse::employeeId)
                .containsExactly("24***01", "24***02");
        assertThat(response.users()).extracting(AdminUserResponse::userName)
                .containsExactly("홍*동", "김*수");
    }

    @Test
    @DisplayName("userId 가 null 이어도 마스킹 때문에 터지지 않는다")
    void nullUserId_isPreserved() {
        AdminUserResponse response = AdminUserResponse.from(detail(null, "2401001", "홍길동"));

        assertThat(response.userId()).isNull();
        assertThat(response.employeeId()).isEqualTo("24***01");
    }
}
