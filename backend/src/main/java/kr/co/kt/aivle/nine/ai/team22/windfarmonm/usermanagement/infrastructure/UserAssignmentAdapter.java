package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssignmentCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssignmentQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.AssignedWindFarmResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link UserAssignmentPort} 어댑터. assetmanagement BC 의 배정 유스케이스로 위임한다.
 */
@Component
@RequiredArgsConstructor
public class UserAssignmentAdapter implements UserAssignmentPort {

    private final AssignmentQueryService assignmentQueryService;
    private final AssignmentCommandService assignmentCommandService;

    @Override
    public Map<Long, List<AssignedWindFarm>> findByUserIds(Collection<Long> userIds) {
        return assignmentQueryService.findByUserIds(userIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(UserAssignmentAdapter::toView).toList()));
    }

    @Override
    public List<AssignedWindFarm> findByUserId(Long userId) {
        return assignmentQueryService.findByUserId(userId).stream()
                .map(UserAssignmentAdapter::toView)
                .toList();
    }

    @Override
    public void replaceAssignments(Long userId, List<Long> windFarmIds) {
        assignmentCommandService.replaceAssignments(userId, windFarmIds);
    }

    private static AssignedWindFarm toView(AssignedWindFarmResult result) {
        return new AssignedWindFarm(result.windFarmId(), result.windFarmName());
    }
}
