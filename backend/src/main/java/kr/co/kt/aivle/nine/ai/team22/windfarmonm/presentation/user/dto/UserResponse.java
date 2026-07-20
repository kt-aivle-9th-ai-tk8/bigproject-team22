package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;

public record UserResponse(
        Long id,
        String employeeId,
        String userName,
        Role role
) {
    public static UserResponse from(UserResult result) {
        return new UserResponse(result.id(), result.employeeId(), result.userName(), result.role());
    }
}
