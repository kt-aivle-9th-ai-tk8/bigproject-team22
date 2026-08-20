package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.SignUpCommand;

/**
 * 회원가입 요청. role 은 클라이언트가 지정하지 않는다(서버가 GUEST 로 고정, 이후 관리자 승인으로 승격).
 */
public record SignUpRequest(
        @NotBlank(message = "사번은 필수입니다.")
        String employeeId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String userName,

        // 길이 제한을 여기서 막는 이유: 초과 시 DB 제약 위반이 UserService 의
        // DataIntegrityViolationException catch 에 걸려 '사번 중복(409)' 으로 잘못 보고된다.
        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 20, message = "연락처는 20자를 넘을 수 없습니다.")
        String phone,

        // 선택 필드다. FE 가입 폼은 부서를 필수로 받으면서도 payload 에 싣지 않고 있어(값이 버려진다),
        // 여기서 필수로 막으면 FE 가 payload 를 고치기 전까지 회원가입이 전부 400 이 된다.
        // FE 가 실어 보내기 시작하면 그때 @NotBlank 로 조일 것.
        @Size(max = 50, message = "부서는 50자를 넘을 수 없습니다.")
        String department
) {
    public SignUpCommand toCommand() {
        return new SignUpCommand(employeeId, password, userName, phone, blankToNull(department));
    }

    /** 빈 문자열은 '미입력'과 같다 — 공백을 저장해 두면 뒤에서 값이 있는 것처럼 보인다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
