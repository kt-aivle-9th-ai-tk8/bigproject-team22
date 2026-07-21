package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

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
