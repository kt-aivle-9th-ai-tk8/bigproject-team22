package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditAction;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.AdminUserManagementService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.AdminUserDetail;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto.AdminUserResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto.AdminUsersResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 전용 사용자 관리 API. context-path(/api) 기준 /admin/users.
 * ADMIN 권한 검사는 AdminRoleInterceptor(/admin/**) 가 수행한다.
 * <p>
 * 권한 변경과 담당 단지 배정은 하나의 PATCH 로 통합되어 있다(별도 배정 엔드포인트 없음).
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserManagementService adminUserManagementService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 사용자 목록 조회: GET /api/admin/users (role 로 필터링, q 로 사번·이름 검색)
     * <p>
     * 응답의 사번·이름은 마스킹되므로 검색은 반드시 이 파라미터로 해야 한다 — FE 가 받은 값으로
     * 거르면 가려진 문자 때문에 매칭되지 않는다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminUsersResponse>> getUsers(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "q", required = false) String keyword) {
        List<AdminUserDetail> users = adminUserManagementService.getUsers(role, keyword);
        // 조회는 개인정보 '처리'라 접속기록 대상이다. 서비스가 아니라 여기서 남기는 이유는
        // getUsers 가 readOnly 트랜잭션이라 그 안에서는 감사 행이 flush 되지 않기 때문이다.
        // 읽기라 업무 변경과 원자적으로 묶일 것도 없어, 트랜잭션 밖이 오히려 맞다.
        eventPublisher.publishEvent(AuditEvent.of(AuditAction.USER_LIST_VIEW, "user", null));
        return ResponseEntity.ok(ApiResponse.success(AdminUsersResponse.from(users)));
    }

    /**
     * 사용자 통합 수정(권한 + 계정 상태 + 담당 단지): PATCH /api/admin/users/{userId}
     * 본문에 없는 항목은 변경하지 않으며, wind_farm_ids 를 빈 배열/null 로 보내면 담당이 전체 해제된다.
     * status 를 SUSPENDED 로 바꾸면 해당 사용자의 세션도 함께 파기된다(재로그인까지 차단).
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(@PathVariable String userId,
                                                                     @RequestBody UpdateUserRequest request) {
        AdminUserDetail result = adminUserManagementService
                .updateUser(ApiIds.toLong(userId), request.toCommand());
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", AdminUserResponse.from(result)));
    }

    /**
     * 사용자 강제 로그아웃: DELETE /api/admin/users/{userId}/session
     * 1인 1세션 정책이므로 대상은 항상 단수다(경로도 단수). 활성 세션이 없었더라도 200 으로 응답한다.
     */
    /**
     * 가입 거절: DELETE /api/admin/users/{userId}
     * <p>
     * 회원가입 대기 화면의 '거절' 버튼이 호출한다. <b>승인 대기(GUEST) 계정만</b> 삭제되고,
     * 이미 승인된 계정이면 400(U003)으로 거부한다 — 삭제는 되돌릴 수 없고 그 계정이 남긴
     * 점검·보고서의 작성자 추적이 끊기므로, 승인된 계정은 정지(PATCH status=SUSPENDED)로 다룬다.
     * 남긴 데이터가 있어 참조 무결성상 지울 수 없으면 409(U004).
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> rejectSignUp(@PathVariable String userId) {
        adminUserManagementService.rejectSignUp(ApiIds.toLong(userId));
        return ResponseEntity.ok(ApiResponse.success("가입 신청을 거절했습니다.", null));
    }

    @DeleteMapping("/{userId}/session")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable String userId) {
        adminUserManagementService.forceLogout(ApiIds.toLong(userId));
        return ResponseEntity.ok(ApiResponse.success("사용자 세션이 강제 종료되었습니다.", null));
    }
}
