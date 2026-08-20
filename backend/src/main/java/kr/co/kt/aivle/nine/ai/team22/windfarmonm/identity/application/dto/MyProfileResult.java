package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;

/**
 * 마이페이지에 실리는 내 계정 정보(원문). 마스킹은 표현 계층에서 한다 —
 * 다른 용도로 재사용될 때 원문이 필요할 수 있어 결과 DTO 는 가리지 않는다.
 *
 * @param department 소속 부서. 가입 시 받지 않아 현재는 null 이다
 */
public record MyProfileResult(
        Long id,
        String employeeId,
        String userName,
        String phone,
        String department,
        Role role
) {
    public static MyProfileResult from(User user) {
        return new MyProfileResult(
                user.getId(),
                user.getEmployeeId(),
                user.getUserName(),
                user.getPhone(),
                user.getDepartment(),
                user.getRole());
    }
}
