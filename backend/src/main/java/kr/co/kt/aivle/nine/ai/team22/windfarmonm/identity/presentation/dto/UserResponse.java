package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security.PiiMasker;

/**
 * 회원가입 응답. 본인 정보라도 마스킹한다 — "응답 DTO 에 평문 개인정보를 싣지 않는다"는 원칙에
 * 예외를 두지 않는다({@link LoginResponse} 와 동일한 판단, 사유는 그쪽 주석 참고).
 * FE 는 이 값을 표시하지 않고 가입 완료 안내만 띄우므로 가려서 잃는 것도 없다.
 */
public record UserResponse(
        String employeeId,
        String userName,
        Role role
) {
    public static UserResponse from(UserResult result) {
        return new UserResponse(
                PiiMasker.mask(result.employeeId()),
                PiiMasker.mask(result.userName()),
                result.role());
    }
}
