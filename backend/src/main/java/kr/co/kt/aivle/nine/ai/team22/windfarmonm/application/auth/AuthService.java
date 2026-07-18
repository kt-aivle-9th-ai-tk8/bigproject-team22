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
     */
    @Transactional
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmployeeId(command.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

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
