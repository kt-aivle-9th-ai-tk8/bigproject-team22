package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;

public record LoginResult(
        Long userId,
        String employeeId,
        String userName,
        Role role
) {
    public static LoginResult from(User user) {
        return new LoginResult(user.getId(), user.getEmployeeId(), user.getUserName(), user.getRole());
    }
}
