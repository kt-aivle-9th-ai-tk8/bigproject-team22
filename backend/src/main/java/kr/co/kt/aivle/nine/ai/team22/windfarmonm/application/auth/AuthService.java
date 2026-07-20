package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto.LoginCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.auth.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.repository.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.ErrorCode;
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
     * 특정 세션(유저)을 강제 로그아웃 처리한다.
     */
    public void forceLogout(String sessionId) {
        sessionManager.invalidate(sessionId);
    }
}
