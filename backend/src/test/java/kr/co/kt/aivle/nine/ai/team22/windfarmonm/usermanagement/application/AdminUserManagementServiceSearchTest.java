package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAdminPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAdminPort.UserAccount;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 사번·이름 검색(q)이 <b>마스킹 전 원문</b>을 대상으로 서버에서 이뤄지는지 고정한다.
 * <p>
 * 이 검색이 서버에 있어야 하는 이유가 곧 이 테스트의 존재 이유다 — 응답은 마스킹되어 나가므로
 * FE 가 받은 값으로는 대조할 수 없고, 검색이 클라이언트로 돌아가는 순간 전 사용자 개인정보를
 * 매번 브라우저로 내려보내야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserManagementServiceSearchTest {

    @Mock
    UserAdminPort userAdminPort;
    @Mock
    UserAssignmentPort userAssignmentPort;
    @Mock
    ApplicationEventPublisher eventPublisher; // 감사 이벤트 발행 — 여기서는 발행 사실만 있으면 된다
    @InjectMocks
    AdminUserManagementService service;

    private static UserAccount account(long id, String employeeId, String userName, Role role) {
        return new UserAccount(id, employeeId, userName, role, UserStatus.ACTIVE, false);
    }

    @BeforeEach
    void stubUsers() {
        when(userAdminPort.findAll()).thenReturn(List.of(
                account(1L, "2401001", "홍길동", Role.MANAGER),
                account(2L, "2401002", "김민수", Role.MANAGER),
                account(3L, "2505001", "홍길순", Role.GUEST),
                account(4L, "9900001", "관리자", Role.ADMIN)));
        when(userAssignmentPort.findByUserIds(anyList())).thenReturn(Map.of());
    }

    private List<String> employeeIdsOf(List<AdminUserDetail> details) {
        return details.stream().map(AdminUserDetail::employeeId).toList();
    }

    @Test
    @DisplayName("q 가 없으면 전체를 반환한다")
    void noKeyword_returnsAll() {
        assertThat(service.getUsers(null, null)).hasSize(4);
    }

    @Test
    @DisplayName("공백만 있는 q 는 필터로 취급하지 않는다")
    void blankKeyword_isIgnored() {
        assertThat(service.getUsers(null, "   ")).hasSize(4);
    }

    @Test
    @DisplayName("사번 부분일치로 검색된다")
    void matchesEmployeeIdPartially() {
        List<AdminUserDetail> found = service.getUsers(null, "24010");

        assertThat(employeeIdsOf(found)).containsExactly("2401001", "2401002");
    }

    @Test
    @DisplayName("이름 부분일치로 검색된다 — 마스킹되면 가려질 가운데 글자도 대조 대상이다")
    void matchesUserNamePartially() {
        // "홍길동" 은 마스킹되면 "홍*동" 이라 FE 가 받은 값으로는 "길" 을 찾을 수 없다.
        List<AdminUserDetail> found = service.getUsers(null, "길");

        assertThat(employeeIdsOf(found)).containsExactly("2401001", "2505001");
    }

    @Test
    @DisplayName("q 앞뒤 공백은 무시한다")
    void keywordIsTrimmed() {
        assertThat(employeeIdsOf(service.getUsers(null, "  김민수  "))).containsExactly("2401002");
    }

    @Test
    @DisplayName("대소문자를 무시하고 검색한다")
    void keywordIsCaseInsensitive() {
        when(userAdminPort.findAll()).thenReturn(List.of(account(5L, "E1001", "Alice", Role.MANAGER)));

        assertThat(service.getUsers(null, "e1001")).hasSize(1);
        assertThat(service.getUsers(null, "ALICE")).hasSize(1);
    }

    @Test
    @DisplayName("role 필터와 q 는 함께 적용된다(AND)")
    void roleAndKeyword_areCombined() {
        List<AdminUserDetail> found = service.getUsers(Role.MANAGER, "홍");

        assertThat(employeeIdsOf(found)).containsExactly("2401001"); // 홍길순은 GUEST 라 제외
    }

    @Test
    @DisplayName("일치하는 사용자가 없으면 빈 목록이다")
    void noMatch_returnsEmpty() {
        assertThat(service.getUsers(null, "존재하지않는값")).isEmpty();
    }

    @Test
    @DisplayName("application 계층 결과에는 원문이 남는다 — 마스킹은 응답 DTO 의 책임이다")
    void applicationLayerKeepsRawValues() {
        List<AdminUserDetail> found = service.getUsers(null, "2401001");

        assertThat(found).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.employeeId()).isEqualTo("2401001");
                    assertThat(detail.userName()).isEqualTo("홍길동");
                });
    }
}
