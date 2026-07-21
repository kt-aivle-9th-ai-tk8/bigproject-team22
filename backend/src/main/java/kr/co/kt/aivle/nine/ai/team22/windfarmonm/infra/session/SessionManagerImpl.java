package kr.co.kt.aivle.nine.ai.team22.windfarmonm.infra.session;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.SessionManager;
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
}
