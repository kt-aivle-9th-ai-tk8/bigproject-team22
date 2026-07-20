package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;

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
