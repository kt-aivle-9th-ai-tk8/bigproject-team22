package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port.SessionManager;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;

    /**
     * 사번/비밀번호 확인 후 로그인. 실패 시 실패 카운트를 누적하고, 임계치 초과 시 계정을 잠근다.
     * 실제 세션 발급(쿠키)은 프레젠테이션 계층에서 HttpSession 에 결과를 저장하며 이루어진다.
     * <p>
     * {@code noRollbackFor}: 비밀번호 불일치 시 던지는 {@link BusinessException}(런타임 예외)이
     * 트랜잭션을 롤백시키면 {@code increaseLoginFailCount()} 증가분이 사라져 계정 잠금이 동작하지 않는다.
     * 따라서 이 예외에 한해 롤백하지 않고 실패 카운트 증가를 커밋한다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmployeeId(command.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // TODO: 잠금/실패사유 노출이 보안상 문제라는 PR 리뷰 존재. 계정 열거 방지를 위해 미존재/불일치/잠김을 동일 응답으로 통일하는 방안 검토 필요
        if (user.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            user.increaseLoginFailCount();
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFailCount();
        return LoginResult.from(user);
    }

    /**
     * 1인 1세션 등록. 로그인으로 새 세션이 발급된 뒤 호출한다.
     * 직전 세션이 남아 있으면(다른 기기 등) 그 세션을 Redis 에서 축출하고,
     * 최신 세션 포인터를 새 세션 id 로 갱신한다.
     * <p>
     * 로그아웃 시에는 이 포인터를 지우지 않는다. 어차피 Redis TTL 만료를 RDB 가 알 수 없어
     * "활성 여부"의 근거로 쓸 수 없기 때문이며, 죽은 id 를 축출하는 것은 무해한 no-op 이다.
     */
    // TODO: 동시 로그인 시 발생 가능한 latestSessionId 저장 경합 해결 필요 (https://github.com/kt-aivle-9th-ai-tk8/bigproject-team22/pull/23#discussion_r3618676948)
    @Transactional
    public void registerSession(Long userId, String newSessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String previousSessionId = user.getLatestSessionId();
        if (previousSessionId != null && !previousSessionId.equals(newSessionId)) {
            sessionManager.invalidate(previousSessionId);
        }
        user.updateLatestSessionId(newSessionId);
    }
}
