package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;

import java.util.List;

/**
 * 사용자 목록 응답 래퍼. 명세상 {@code data.users} 로 감싼다.
 */
public record AdminUsersResponse(
        List<AdminUserResponse> users
) {
    public static AdminUsersResponse from(List<AdminUserDetail> details) {
        return new AdminUsersResponse(details.stream()
                .map(AdminUserResponse::from)
                .toList());
    }
}
