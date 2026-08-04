package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

import java.util.List;

/**
 * 사용자 계정(identity BC) 접근 포트. 소비자(usermanagement)가 소유하고 어댑터가 identity 로 위임한다.
 */
public interface UserAdminPort {

    /** 전체 사용자 계정(세션 활성 여부 포함). */
    List<UserAccount> findAll();

    /** 단일 사용자 계정. 없으면 USER_NOT_FOUND. */
    UserAccount findById(Long userId);

    /** 권한 변경(활성 세션은 파기되어 재로그인 시 새 권한이 적용된다). */
    UserAccount changeRole(Long userId, Role role);

    /** 강제 로그아웃(세션 파기). */
    void forceLogout(Long userId);

    /** 사용자 계정 요약. */
    record UserAccount(
            Long id,
            String employeeId,
            String userName,
            Role role,
            boolean sessionActive
    ) {
        public boolean isAdmin() {
            return role == Role.ADMIN;
        }
    }
}
