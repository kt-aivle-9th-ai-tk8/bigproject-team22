package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;

/**
 * 계정 상태. <b>로그인 가능 여부의 단일 판단 근거</b>다.
 * <p>
 * 자동 잠금(로그인 실패 임계 초과)과 관리자에 의한 명시적 차단을 별도 값으로 구분하지 않고
 * {@link #SUSPENDED} 하나로 표현한다. 잠금 사유가 아니라 "지금 로그인할 수 있는가"만이
 * 인증 판단에 필요하기 때문이다. 실패 횟수({@code loginFailCount})는 감사 목적으로 남지만
 * 잠금 판단에는 쓰이지 않는다.
 */
public enum UserStatus {

    /** 정상. 로그인 가능. */
    ACTIVE,

    /** 정지. 로그인 불가(A003). 실패 임계 초과로 자동 전이되거나 관리자가 직접 지정한다. */
    SUSPENDED
}
