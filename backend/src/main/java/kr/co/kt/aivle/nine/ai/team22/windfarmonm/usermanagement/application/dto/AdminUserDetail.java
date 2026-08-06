package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort.AssignedWindFarm;

import java.util.List;

/**
 * 관리자 화면의 사용자 1건(계정 + 담당 단지).
 *
 * @param status      계정 상태(ACTIVE/SUSPENDED). 세션 활성 여부와는 별개다 —
 *                    로그아웃 상태(sessionActive=false)여도 계정은 ACTIVE 일 수 있다.
 * @param assignments ADMIN 은 전체 열람이므로 {@code null}(해당 없음),
 *                    그 외 사용자는 담당이 없어도 빈 목록(null 아님)
 */
public record AdminUserDetail(
        Long userId,
        String employeeId,
        String userName,
        Role role,
        UserStatus status,
        boolean sessionActive,
        List<AssignedWindFarm> assignments
) {
}
