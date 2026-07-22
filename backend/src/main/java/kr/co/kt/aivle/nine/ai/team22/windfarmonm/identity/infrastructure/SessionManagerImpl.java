package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.SessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

/**
 * Spring Session(Redis) 저장소를 이용한 {@link SessionManager} 어댑터.
 */
@Component
@RequiredArgsConstructor
public class SessionManagerImpl implements SessionManager {

    /**
     * SessionRepository는 {@link org.springframework.session.data.redis.RedisSessionRepository}로
     * {@link kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config.SessionConfig}에서 빈 주입됨.
     */
    private final SessionRepository<? extends Session> sessionRepository;

    @Override
    public void invalidate(String sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    /**
     * findById 는 세션이 없거나 만료됐으면 null 을 반환한다.
     * 즉 Redis TTL 만료도 여기서 자연히 false 로 반영된다.
     */
    @Override
    public boolean exists(String sessionId) {
        return sessionId != null && sessionRepository.findById(sessionId) != null;
    }
}
