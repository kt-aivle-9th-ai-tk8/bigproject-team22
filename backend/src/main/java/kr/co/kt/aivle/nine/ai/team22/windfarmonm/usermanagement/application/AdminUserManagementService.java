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
import java.util.Locale;
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
     * 사용자 목록 조회. {@code role} 이 주어지면 해당 권한만, {@code keyword} 가 주어지면 사번 또는
     * 이름에 그 문자열을 포함하는 사용자만 반환한다(대소문자 무시, 공백만 있으면 필터 없음).
     * 담당 단지는 비-ADMIN 사용자들만 한 번에 조회한다(N+1 방지).
     * <p>
     * <b>검색이 서버에 있어야 하는 이유</b>: 응답의 사번·이름은 마스킹되어 나가므로 FE 가 받은 값으로는
     * 대조할 수 없다. 원문을 가진 서버에서 걸러야 하고, 그래야 검색어에 걸린 몇 건만 내려가
     * 전 사용자 개인정보가 매번 브라우저로 흘러가지도 않는다.
     */
    @Transactional(readOnly = true)
    public List<AdminUserDetail> getUsers(Role roleFilter, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);

        List<UserAccount> accounts = userAdminPort.findAll().stream()
                .filter(account -> roleFilter == null || account.role() == roleFilter)
                .filter(account -> matchesKeyword(account, normalizedKeyword))
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
     * 사용자 통합 수정(권한 + 계정 상태 + 담당 단지). 본문에 없는 항목은 변경하지 않는다.
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

        if (command.statusProvided()) {
            if (command.status() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT); // status 는 NN — 명시적 null 은 계약 위반
            }
            if (command.status() != account.status()) {
                account = userAdminPort.changeStatus(userId, command.status());
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
    /**
     * 가입 거절. 승인 대기(GUEST) 계정만 삭제되며, 이미 승인된 계정은 400(U003)으로 거부된다 —
     * 삭제는 되돌릴 수 없고 그 계정이 남긴 기록의 작성자 추적이 끊기기 때문이다(정지로 다룰 것).
     */
    public void rejectSignUp(Long userId) {
        userAdminPort.rejectSignUp(userId);
    }

    public void forceLogout(Long userId) {
        userAdminPort.forceLogout(userId);
    }

    /** 검색어 정규화. null/공백만 있는 값은 "필터 없음"(null)으로 취급한다. */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** 사번 또는 이름 부분일치. 대조 대상은 <b>마스킹 전 원문</b>이다(마스킹은 응답 DTO 에서 일어난다). */
    private static boolean matchesKeyword(UserAccount account, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return containsIgnoreCase(account.employeeId(), normalizedKeyword)
                || containsIgnoreCase(account.userName(), normalizedKeyword);
    }

    private static boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private static AdminUserDetail toDetail(UserAccount account, List<AssignedWindFarm> assignments) {
        return new AdminUserDetail(
                account.id(),
                account.employeeId(),
                account.userName(),
                account.role(),
                account.status(),
                account.sessionActive(),
                assignments);
    }
}
