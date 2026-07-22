package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

public record UserResponse(
        String employeeId,
        String userName,
        Role role
) {
    public static UserResponse from(UserResult result) {
        return new UserResponse(result.employeeId(), result.userName(), result.role());
    }
}
