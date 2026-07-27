package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import jakarta.validation.constraints.NotNull;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

/**
 * 사용자 권한 승인/변경 요청. 예: GUEST → MANAGER/ADMIN 승격.
 */
public record ChangeRoleRequest(
        @NotNull(message = "권한(role)은 필수입니다.")
        Role role
) {
}
