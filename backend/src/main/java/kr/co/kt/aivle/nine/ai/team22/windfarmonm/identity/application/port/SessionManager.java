package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port;

/**
 * 세션 저장소 조작 포트. 구현(Redis 세션 어댑터)은 infrastructure 레이어에 둔다.
 */
public interface SessionManager {

    /** 특정 세션을 강제로 파기한다(강제 로그아웃). 없는 세션이면 무해한 no-op. */
    void invalidate(String sessionId);

    /**
     * 해당 세션이 저장소에 실제로 살아있는지 확인한다.
     * RDB 의 세션 포인터는 TTL 만료를 반영하지 못하므로, "로그인 중" 판정은 이 메서드로 한다.
     */
    boolean exists(String sessionId);
}
