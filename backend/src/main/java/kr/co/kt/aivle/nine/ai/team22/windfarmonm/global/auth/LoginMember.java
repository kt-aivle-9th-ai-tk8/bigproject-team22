package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

import java.io.Serializable;

/**
 * 세션에 저장되는 로그인 사용자 정보. Redis 직렬화 대상이므로 {@link Serializable} 이어야 한다.
 */
public record LoginMember(
        Long userId,
        String employeeId,
        String userName,
        Role role
) implements Serializable {
}
