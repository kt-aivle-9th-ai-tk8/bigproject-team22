package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security.PiiMasker;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;

import java.util.List;

/**
 * 관리자 화면의 사용자 1건. id 는 JS Number 정밀도 손실을 피하기 위해 문자열로 직렬화한다.
 * <p>
 * <b>타인의 개인정보가 나가는 유일한 응답이므로 사번·이름은 마스킹한다.</b> ADMIN 도 예외가 아니다 —
 * 지금 이 화면의 조작(권한 변경·계정 잠금·강제 로그아웃·단지 배정)은 모두 {@code userId} 로 동작하므로
 * 평문 사번이 필요한 기능이 없다. {@code userId} 는 FE 가 그 조작에 쓰는 키라 마스킹 대상이 아니다.
 *
 * @param status      계정 상태(ACTIVE/SUSPENDED). 화면의 잠금 표시/해제 조작 대상이다.
 * @param assignments ADMIN 은 {@code null}, 그 외는 담당이 없어도 빈 배열
 */
public record AdminUserResponse(
        String userId,
        String employeeId,
        String userName,
        Role role,
        UserStatus status,
        boolean sessionActive,
        List<AssignmentResponse> assignments
) {
    public static AdminUserResponse from(AdminUserDetail detail) {
        return new AdminUserResponse(
                detail.userId() == null ? null : String.valueOf(detail.userId()),
                PiiMasker.mask(detail.employeeId()),
                PiiMasker.mask(detail.userName()),
                detail.role(),
                detail.status(),
                detail.sessionActive(),
                detail.assignments() == null ? null : detail.assignments().stream()
                        .map(AssignmentResponse::from)
                        .toList());
    }
}
