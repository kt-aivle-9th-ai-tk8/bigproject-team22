package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;

import java.util.List;

/**
 * 관리자 화면의 사용자 1건. id 는 JS Number 정밀도 손실을 피하기 위해 문자열로 직렬화한다.
 *
 * @param assignments ADMIN 은 {@code null}, 그 외는 담당이 없어도 빈 배열
 */
public record AdminUserResponse(
        String userId,
        String employeeId,
        String userName,
        Role role,
        boolean sessionActive,
        List<AssignmentResponse> assignments
) {
    public static AdminUserResponse from(AdminUserDetail detail) {
        return new AdminUserResponse(
                detail.userId() == null ? null : String.valueOf(detail.userId()),
                detail.employeeId(),
                detail.userName(),
                detail.role(),
                detail.sessionActive(),
                detail.assignments() == null ? null : detail.assignments().stream()
                        .map(AssignmentResponse::from)
                        .toList());
    }
}
