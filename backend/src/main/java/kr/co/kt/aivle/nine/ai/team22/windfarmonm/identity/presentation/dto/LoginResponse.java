package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

public record LoginResponse(
        String employeeId,
        String userName,
        Role role
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.employeeId(), result.userName(), result.role());
    }
}
