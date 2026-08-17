package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.port.SessionManager;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     * 특정 역할의 사용자 id 목록. <b>인가를 하지 않는</b> 내부 조회 — 알림 fan-out 의 ADMIN 수신자 산출 등
     * 시스템 내부 용도로만 쓴다(컨트롤러 접근통제는 AdminRoleInterceptor 가 담당).
     */
    @Transactional(readOnly = true)
    public List<Long> findUserIdsByRole(Role role) {
        return userRepository.findUserIdsByRole(role);
    }

    /** 단일 사용자 조회(세션 활성 여부 포함). 없으면 {@link ErrorCode#USER_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public AdminUserResult getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return AdminUserResult.of(user, sessionManager.exists(user.getLatestSessionId()));
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

    /**
     * 계정 상태 변경(정지/활성화).
     * <p>
     * 정지시키면 <b>세션도 함께 파기</b>한다. 상태만 바꾸면 이미 로그인해 있던 사용자가 세션이 만료될 때까지
     * 계속 이용할 수 있어 "차단"이 성립하지 않기 때문이다. 활성화 시 실패 카운트 초기화는 도메인
     * ({@link User#changeStatus})이 담당한다.
     */
    @Transactional
    public AdminUserResult changeStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeStatus(status);

        if (status == UserStatus.SUSPENDED) {
            invalidateLatestSession(user);
            return AdminUserResult.of(user, false); // 방금 파기했으므로 비활성
        }
        return AdminUserResult.of(user, sessionManager.exists(user.getLatestSessionId()));
    }

    /**
     * 가입 거절 — <b>승인 대기(GUEST) 계정만</b> 삭제한다.
     * <p>
     * 이미 승인된 계정(MANAGER/ADMIN)은 삭제하지 않는다: 그가 남긴 점검·보고서의 작성자 추적이
     * 끊기고 되돌릴 수 없기 때문이다. 그런 계정은 정지(SUSPENDED)로 다룬다.
     * <p>
     * 삭제 전에 세션을 파기한다 — GUEST 는 로그인이 차단되지만(A004), 승인 후 GUEST 로 되돌린
     * 계정에는 이전 세션이 남아 있을 수 있고, 계정이 사라진 뒤 남은 세션은 주인 없는 접근이 된다.
     * 남긴 데이터가 있어 FK 로 막히면 {@link ErrorCode#USER_HAS_REFERENCES}(409) 로 번역한다.
     */
    @Transactional
    public void rejectSignUp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != Role.GUEST) {
            throw new BusinessException(ErrorCode.USER_NOT_PENDING);
        }
        invalidateLatestSession(user);
        try {
            userRepository.delete(user);
            userRepository.flush(); // FK 위반을 이 트랜잭션 안에서 잡아 번역한다(커밋 시점이면 놓친다)
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.USER_HAS_REFERENCES);
        }
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
