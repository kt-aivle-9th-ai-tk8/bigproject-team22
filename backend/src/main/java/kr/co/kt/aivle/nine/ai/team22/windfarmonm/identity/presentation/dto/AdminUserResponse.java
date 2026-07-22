package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

public record AdminUserResponse(
        String employeeId,
        String userName,
        Role role,
        boolean sessionActive
) {
    public static AdminUserResponse from(AdminUserResult result) {
        return new AdminUserResponse(
                result.employeeId(),
                result.userName(),
                result.role(),
                result.sessionActive()
        );
    }
}
