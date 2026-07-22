package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 전용 사용자 관리 유스케이스. 접근 통제(ADMIN)는 AdminRoleInterceptor 가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionManager sessionManager;

    /**
     * 전체 사용자 목록 조회. 로그인 여부는 RDB 포인터가 아니라 세션 저장소에서 실제 확인한다
     * (TTL 만료는 RDB 에 반영되지 않으므로).
     */
    @Transactional(readOnly = true)
    public List<AdminUserResult> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> AdminUserResult.of(user, sessionManager.exists(user.getLatestSessionId())))
                .toList();
    }

    /**
     * 사용자 권한 승인/변경. 변경이 즉시 반영되도록(세션에는 이전 권한이 스냅샷됨)
     * 대상 사용자의 활성 세션을 강제 종료하여 재로그인 시 새 권한이 적용되게 한다.
     */
    @Transactional
    public AdminUserResult changeRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeRole(role);
        invalidateLatestSession(user);
        return AdminUserResult.of(user, false); // 방금 파기했으므로 비활성
    }

    /** 특정 사용자를 강제 로그아웃(세션 파기). 1인 1세션이므로 세션 id 없이 user_id 만으로 처리. */
    @Transactional
    public void forceLogout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        invalidateLatestSession(user);
    }

    /**
     * 세션 저장소에서만 파기하고 latestSessionId 는 남겨둔다.
     * 이 컬럼은 "활성 표시"가 아니라 축출 포인터/최근 이력이며, 죽은 id 를 지우는 것은 무해한 no-op 이다.
     */
    private void invalidateLatestSession(User user) {
        String sessionId = user.getLatestSessionId();
        if (sessionId != null) {
            sessionManager.invalidate(sessionId);
        }
    }
}
