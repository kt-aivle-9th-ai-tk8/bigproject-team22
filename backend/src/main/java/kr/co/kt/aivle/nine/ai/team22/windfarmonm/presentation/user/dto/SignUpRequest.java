package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user.dto;

import jakarta.validation.constraints.NotBlank;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.SignUpCommand;

/**
 * 회원가입 요청. role 은 클라이언트가 지정하지 않는다(서버가 GUEST 로 고정, 이후 관리자 승인으로 승격).
 */
public record SignUpRequest(
        @NotBlank(message = "사번은 필수입니다.")
        String employeeId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String userName
) {
    public SignUpCommand toCommand() {
        return new SignUpCommand(employeeId, password, userName);
    }
}
