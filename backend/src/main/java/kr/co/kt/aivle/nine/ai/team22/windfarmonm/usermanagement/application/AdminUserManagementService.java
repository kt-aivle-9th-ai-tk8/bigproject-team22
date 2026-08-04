package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.UpdateUserCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAdminPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAdminPort.UserAccount;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort.AssignedWindFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 관리자 사용자 관리 유스케이스(조합 계층).
 * <p>
 * 사용자 계정(identity)과 담당 단지 배정(assetmanagement)을 조합한다. 두 BC 는 서로를 알지 못하고
 * 이 계층이 각 BC 의 포트를 통해 단방향으로만 참조한다(순환 의존 없음).
 */
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private final UserAdminPort userAdminPort;
    private final UserAssignmentPort userAssignmentPort;

    /**
     * 사용자 목록 조회. {@code role} 이 주어지면 해당 권한만 필터링한다.
     * 담당 단지는 비-ADMIN 사용자들만 한 번에 조회한다(N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<AdminUserDetail> getUsers(Role roleFilter) {
        List<UserAccount> accounts = userAdminPort.findAll().stream()
                .filter(account -> roleFilter == null || account.role() == roleFilter)
                .toList();

        List<Long> assignableUserIds = accounts.stream()
                .filter(account -> !account.isAdmin())
                .map(UserAccount::id)
                .toList();
        Map<Long, List<AssignedWindFarm>> assignments = userAssignmentPort.findByUserIds(assignableUserIds);

        return accounts.stream()
                .map(account -> toDetail(account,
                        account.isAdmin() ? null : assignments.getOrDefault(account.id(), List.of())))
                .toList();
    }

    /**
     * 사용자 통합 수정(권한 + 담당 단지). 본문에 없는 항목은 변경하지 않는다.
     * <p>
     * ADMIN 은 전체 단지를 열람하므로 담당 배정을 가질 수 없다 — ADMIN 에게 단지를 배정하려 하면
     * {@link ErrorCode#INVALID_INPUT}. (ADMIN 으로 승격하면서 배정을 비우는 것은 허용된다.)
     */
    @Transactional
    public AdminUserDetail updateUser(Long userId, UpdateUserCommand command) {
        UserAccount account = userAdminPort.findById(userId); // 없으면 USER_NOT_FOUND

        if (command.roleProvided()) {
            if (command.role() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT); // role 은 NN — 명시적 null 은 계약 위반
            }
            if (command.role() != account.role()) {
                account = userAdminPort.changeRole(userId, command.role());
            }
        }

        if (command.windFarmIdsProvided()) {
            List<Long> windFarmIds = command.windFarmIds() == null ? List.of() : command.windFarmIds();
            if (account.isAdmin() && !windFarmIds.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            userAssignmentPort.replaceAssignments(userId, windFarmIds);
        } else if (account.isAdmin()) {
            // ADMIN 으로 승격된 경우 남아있던 배정은 의미가 없으므로 정리한다.
            userAssignmentPort.replaceAssignments(userId, List.of());
        }

        return toDetail(account,
                account.isAdmin() ? null : userAssignmentPort.findByUserId(userId));
    }

    /** 강제 로그아웃(세션 파기). */
    @Transactional
    public void forceLogout(Long userId) {
        userAdminPort.forceLogout(userId);
    }

    private static AdminUserDetail toDetail(UserAccount account, List<AssignedWindFarm> assignments) {
        return new AdminUserDetail(
                account.id(),
                account.employeeId(),
                account.userName(),
                account.role(),
                account.sessionActive(),
                assignments);
    }
}
