package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

/**
 * 회원가입 입력.
 *
 * @param department 소속 부서. 선택값이라 미입력이면 null 이다
 */
public record SignUpCommand(
        String employeeId,
        String password,
        String userName,
        String phone,
        String department
) {
}
