package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;

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
