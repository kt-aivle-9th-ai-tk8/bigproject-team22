package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

public record SignUpCommand(
        String employeeId,
        String password,
        String userName,
        String phone
) {
}
