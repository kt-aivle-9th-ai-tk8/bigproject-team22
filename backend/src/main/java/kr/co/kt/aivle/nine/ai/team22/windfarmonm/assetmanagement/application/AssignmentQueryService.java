package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.AssignedWindFarmResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarm;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 담당 배정 조회 유스케이스. 다른 BC(사용자 관리)는 이 서비스를 통해서만 배정 정보를 읽는다.
 */
@Service
@RequiredArgsConstructor
public class AssignmentQueryService {

    private final AssignmentRepository assignmentRepository;
    private final WindFarmRepository windFarmRepository;

    /**
     * 여러 사용자의 담당 단지를 한 번에 조회한다(사용자별 배정 조회 N+1 방지).
     * 배정이 없는 사용자는 결과 맵에 키가 없다(호출측에서 빈 목록으로 해석).
     *
     * @return userId → 담당 단지 목록(단지명 오름차순)
     */
    @Transactional(readOnly = true)
    public Map<Long, List<AssignedWindFarmResult>> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Assignment> assignments = assignmentRepository.findByUserIdIn(userIds);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = windFarmNames(assignments.stream()
                .map(Assignment::getWindFarmId)
                .distinct()
                .toList());

        Map<Long, List<AssignedWindFarmResult>> result = new LinkedHashMap<>();
        for (Assignment assignment : assignments) {
            result.computeIfAbsent(assignment.getUserId(), k -> new java.util.ArrayList<>())
                    .add(new AssignedWindFarmResult(
                            assignment.getWindFarmId(),
                            names.get(assignment.getWindFarmId())));
        }
        result.values().forEach(list -> list.sort(
                Comparator.comparing(AssignedWindFarmResult::windFarmName,
                        Comparator.nullsLast(Comparator.naturalOrder()))));
        return result;
    }

    /** 단일 사용자의 담당 단지 목록(없으면 빈 목록). */
    @Transactional(readOnly = true)
    public List<AssignedWindFarmResult> findByUserId(Long userId) {
        return findByUserIds(List.of(userId)).getOrDefault(userId, List.of());
    }

    private Map<Long, String> windFarmNames(List<Long> windFarmIds) {
        return windFarmRepository.findAllByIdIn(windFarmIds).stream()
                .collect(Collectors.toMap(WindFarm::getId, WindFarm::getName));
    }
}
