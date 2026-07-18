package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;

public record SignUpCommand(
        String employeeId,
        String password,
        String userName,
        Role role
) {
}
