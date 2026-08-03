package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.AdminUserService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.AdminUserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.AdminUserResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.ChangeRoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 전용 사용자 관리 API. context-path(/api) 기준 /admin/users.
 * ADMIN 권한 검사는 AdminRoleInterceptor(/admin/**) 가 수행한다.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 사용자 리스트 조회: GET /api/admin/users */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getUsers() {
        List<AdminUserResponse> body = adminUserService.getUsers().stream()
                .map(AdminUserResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 사용자 권한 관리(승인/변경): PATCH /api/admin/users/{userId} */
    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(@PathVariable String userId,
                                                                     @Valid @RequestBody ChangeRoleRequest request) {
        AdminUserResult result = adminUserService.changeRole(ApiIds.toLong(userId), request.role());
        return ResponseEntity.ok(ApiResponse.success("권한이 변경되었습니다.", AdminUserResponse.from(result)));
    }

    /** 사용자 강제 로그아웃: DELETE /api/admin/users/{userId}/sessions */
    @DeleteMapping("/{userId}/sessions")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable String userId) {
        adminUserService.forceLogout(ApiIds.toLong(userId));
        return ResponseEntity.ok(ApiResponse.success("사용자 세션이 강제 종료되었습니다.", null));
    }
}
