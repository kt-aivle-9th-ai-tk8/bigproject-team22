package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.AdminUserService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link UserAdminPort} 어댑터. identity BC 의 사용자 관리 유스케이스로 위임한다.
 * (identity 는 usermanagement 를 알지 못한다 — 의존은 단방향이다.)
 */
@Component
@RequiredArgsConstructor
public class UserAdminAdapter implements UserAdminPort {

    private final AdminUserService adminUserService;

    @Override
    public List<UserAccount> findAll() {
        return adminUserService.getUsers().stream()
                .map(UserAdminAdapter::toAccount)
                .toList();
    }

    @Override
    public UserAccount findById(Long userId) {
        return toAccount(adminUserService.getUser(userId));
    }

    @Override
    public UserAccount changeRole(Long userId, Role role) {
        return toAccount(adminUserService.changeRole(userId, role));
    }

    @Override
    public void forceLogout(Long userId) {
        adminUserService.forceLogout(userId);
    }

    private static UserAccount toAccount(AdminUserResult result) {
        return new UserAccount(
                result.id(),
                result.employeeId(),
                result.userName(),
                result.role(),
                result.sessionActive());
    }
}
