package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth;

/**
 * 세션 저장소 조작 포트. 구현(Redis 세션 어댑터)은 infra 레이어에 둔다.
 */
public interface SessionManager {

    /** 특정 세션을 강제로 파기한다(강제 로그아웃). */
    void invalidate(String sessionId);
}
