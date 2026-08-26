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

    /**
     * 관리자 여부. 각 BC 의 표현계층이 {@code Role} 을 직접 임포트해 비교하지 않도록 여기서 판정한다
     * (권한 판정 규칙이 바뀌어도 호출부가 영향받지 않는다).
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * 읽기 전용 데모 세션 여부. {@code DemoReadOnlyInterceptor} 가 이 값으로 쓰기 요청을 차단한다
     * (역할 판정을 여기 한 곳에 모아, 호출부가 {@code Role} 을 직접 비교하지 않게 한다).
     */
    public boolean isDemo() {
        return role == Role.DEMO;
    }
}
