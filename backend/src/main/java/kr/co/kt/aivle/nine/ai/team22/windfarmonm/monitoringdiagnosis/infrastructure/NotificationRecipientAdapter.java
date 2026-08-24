package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssignmentQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.AdminUserService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.port.NotificationRecipientPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link NotificationRecipientPort} 어댑터. identity(ADMIN 조회)·assetmanagement(담당자 조회)의
 * application 서비스로만 위임한다(각 BC 의 domain/리포지토리 직접 접근 금지 — 의존은 단방향).
 * 두 조회 모두 인가 없는 내부 조회이며, fan-out 은 시스템 트리거(이상 보고서 생성)로만 일어난다.
 */
@Component
@RequiredArgsConstructor
public class NotificationRecipientAdapter implements NotificationRecipientPort {

    private final AdminUserService adminUserService;
    private final AssignmentQueryService assignmentQueryService;

    @Override
    public List<Long> adminUserIds() {
        return adminUserService.findUserIdsByRole(Role.ADMIN);
    }

    @Override
    public List<Long> assignedUserIds(Long windFarmId) {
        return assignmentQueryService.findUserIdsByWindFarmId(windFarmId);
    }
}
