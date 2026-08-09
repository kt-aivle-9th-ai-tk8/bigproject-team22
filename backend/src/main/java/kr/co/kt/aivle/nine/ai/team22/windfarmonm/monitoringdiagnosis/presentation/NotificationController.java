package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.presentation;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.NotificationCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.NotificationQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.presentation.dto.NotificationResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알림 API. context-path(/api) 기준 /notifications.
 * <p>
 * 자기 알림만 다룬다 — 타인 알림 접근은 404 로 응답한다(존재 은닉, 별도 403 코드 없음).
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    /** 내 알림 목록: GET /api/notifications (unread=true 면 안 읽은 것만) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestParam(name = "unread", required = false, defaultValue = "false") boolean unread,
            @Login LoginMember member) {
        List<NotificationResponse> body = notificationQueryService.getNotifications(member.userId(), unread).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 알림 읽음 처리(멱등): PATCH /api/notifications/{notificationId}/read */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable String notificationId,
                                                      @Login LoginMember member) {
        notificationCommandService.markRead(member.userId(), ApiIds.toLong(notificationId));
        return ResponseEntity.ok(ApiResponse.success("읽음 처리되었습니다.", null));
    }

    /** 알림 삭제: DELETE /api/notifications/{notificationId} */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable String notificationId,
                                                                @Login LoginMember member) {
        notificationCommandService.delete(member.userId(), ApiIds.toLong(notificationId));
        return ResponseEntity.ok(ApiResponse.success("삭제되었습니다.", null));
    }
}
