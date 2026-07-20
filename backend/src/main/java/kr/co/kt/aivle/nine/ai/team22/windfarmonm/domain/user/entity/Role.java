package kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity;

/**
 * 계정 권한. 회원가입 시 role 파라미터로 지정된다.
 */
public enum Role {
    ADMIN,
    MANAGER,
    GUEST   // pending to approve sign-up
}
