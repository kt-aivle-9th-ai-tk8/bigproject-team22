package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;

public record UserResult(
        Long id,
        String employeeId,
        String userName,
        Role role
) {
    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getEmployeeId(), user.getUserName(), user.getRole());
    }
}
