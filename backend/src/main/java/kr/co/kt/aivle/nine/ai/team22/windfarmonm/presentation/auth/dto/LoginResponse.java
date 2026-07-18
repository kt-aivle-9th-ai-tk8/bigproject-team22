package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.auth.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;

public record LoginResponse(
        Long userId,
        String employeeId,
        String userName,
        Role role
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.userId(), result.employeeId(), result.userName(), result.role());
    }
}
