package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port.SessionManager;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
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
     * 사번/비밀번호 확인 후 로그인. 실패 시 실패 카운트를 누적하고, 임계치 도달 시 계정이 정지된다.
     * 실제 세션 발급(쿠키)은 프레젠테이션 계층에서 HttpSession 에 결과를 저장하며 이루어진다.
     * <p>
     * 차단 판단은 {@code status} 하나로 일원화되어 있다(실패 누적에 의한 자동 정지와 관리자의 명시적
     * 차단이 같은 경로로 걸린다). 실패 카운트는 감사 기록으로만 남는다.
     * <p>
     * {@code noRollbackFor}: 비밀번호 불일치 시 던지는 {@link BusinessException}(런타임 예외)이
     * 트랜잭션을 롤백시키면 {@code increaseLoginFailCount()} 증가분과 그에 따른 상태 전이가 사라져
     * 계정 잠금이 동작하지 않는다. 따라서 이 예외에 한해 롤백하지 않고 커밋한다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmployeeId(command.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 잠김을 별도 코드(A003)로 알리는 것은 FE 요구사항이다(사용자에게 관리자 문의를 안내해야 함).
        // 계정 열거 관점에서는 미존재/불일치와 구분되지 않는 편이 안전하지만, 화면 요구가 우선한다.
        if (user.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            user.increaseLoginFailCount();
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFailCount();

        // 인증(비밀번호) 성공 '후'에 승인 여부(role==GUEST)를 확인한다. 순서가 중요하다:
        //  ① 비밀번호를 먼저 검증해야 '승인 대기' 상태가 비밀번호를 모르는 제3자에게 노출되지 않는다(계정 열거 방지).
        //     비밀번호가 틀린 GUEST 는 A004 가 아니라 INVALID_CREDENTIALS 로 응답되어 미존재/일반 계정과 구분되지 않는다.
        //  ② 그래야 A004(403)가 '신원은 확인됐으나 아직 미승인'이라는 의미로 정확해진다.
        // 승인되면 changeRole 로 MANAGER/ADMIN 으로 승격된다(status 축과 별개).
        if (user.getRole() == Role.GUEST) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING);
        }

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
