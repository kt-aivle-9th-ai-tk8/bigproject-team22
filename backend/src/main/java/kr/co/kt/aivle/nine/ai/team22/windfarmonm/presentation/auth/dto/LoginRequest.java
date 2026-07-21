package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto.LoginCommand;

public record LoginRequest(
        @NotBlank(message = "사번은 필수입니다.")
        String employeeId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
    public LoginCommand toCommand() {
        return new LoginCommand(employeeId, password);
    }
}
