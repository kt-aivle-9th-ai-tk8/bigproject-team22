package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;

public record AdminUserResult(
        Long id,
        String employeeId,
        String userName,
        Role role,
        boolean sessionActive
) {
    /**
     * sessionActive 는 RDB 의 latestSessionId 로 판단하면 안 된다.
     * 세션은 Redis TTL 로 만료되지만 그 사실이 RDB 에 반영되지 않아 거짓 양성이 되기 때문이다.
     * 따라서 세션 저장소에서 실제 확인한 값을 주입받는다.
     */
    public static AdminUserResult of(User user, boolean sessionActive) {
        return new AdminUserResult(
                user.getId(),
                user.getEmployeeId(),
                user.getUserName(),
                user.getRole(),
                sessionActive
        );
    }
}
