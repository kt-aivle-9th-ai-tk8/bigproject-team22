package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.SignUpCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;

public record SignUpRequest(
        @NotBlank(message = "사번은 필수입니다.")
        String employeeId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String userName,

        @NotNull(message = "권한(role)은 필수입니다.")
        Role role
) {
    public SignUpCommand toCommand() {
        return new SignUpCommand(employeeId, password, userName, role);
    }
}
