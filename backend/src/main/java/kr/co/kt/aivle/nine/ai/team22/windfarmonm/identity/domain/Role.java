package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;

/**
 * 계정 권한. 회원가입 시 role 파라미터로 지정된다.
 */
public enum Role {
    ADMIN,
    MANAGER,
    GUEST,  // pending to approve sign-up
    // 읽기 전용 데모 principal. 담당(Assignment) 기반 열람 권한은 일반 사용자와 동일하게 받되,
    // 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)은 DemoReadOnlyInterceptor 가 차단한다.
    // 데모 진입(/auth/demo)에서만 세션에 부여되는 값이며, 관리자 권한변경으로는 지정할 수 없다.
    DEMO
}
