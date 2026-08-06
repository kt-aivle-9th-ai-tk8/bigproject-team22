package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarm;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarmRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 담당 배정 변경 유스케이스. 사용자 실존 검증은 호출측(사용자 관리 BC)이 수행한다.
 */
@Service
@RequiredArgsConstructor
public class AssignmentCommandService {

    private final AssignmentRepository assignmentRepository;
    private final WindFarmRepository windFarmRepository;

    /**
     * 사용자의 담당 단지를 요청 목록으로 전체 교체한다(빈 목록 = 전체 해제).
     * <p>
     * 전량 삭제 후 재삽입하지 않고 <b>차집합만</b> 삭제/삽입한다. 같은 트랜잭션에서 삭제와 삽입이 섞이면
     * Hibernate 가 INSERT 를 DELETE 보다 먼저 플러시해 유지되는 배정에서 PK 충돌이 날 수 있고,
     * 불필요한 쓰기도 발생하기 때문이다.
     *
     * @throws BusinessException 존재하지 않는 단지가 포함된 경우 {@link ErrorCode#WIND_FARM_NOT_FOUND}
     */
    @Transactional
    public void replaceAssignments(Long userId, List<Long> windFarmIds) {
        List<Long> requested = (windFarmIds == null ? List.<Long>of() : windFarmIds).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (!requested.isEmpty()) {
            long found = windFarmRepository.findAllByIdIn(requested).stream()
                    .map(WindFarm::getId)
                    .distinct()
                    .count();
            if (found != requested.size()) {
                throw new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND);
            }
        }

        Set<Long> current = Set.copyOf(assignmentRepository.findWindFarmIdsByUserId(userId));
        Set<Long> target = Set.copyOf(requested);

        List<Long> toRemove = current.stream().filter(id -> !target.contains(id)).toList();
        List<Long> toAdd = requested.stream().filter(id -> !current.contains(id)).toList();

        if (!toRemove.isEmpty()) {
            assignmentRepository.deleteByUserIdAndWindFarmIdIn(userId, toRemove);
        }
        if (!toAdd.isEmpty()) {
            assignmentRepository.saveAll(toAdd.stream()
                    .map(windFarmId -> Assignment.of(userId, windFarmId))
                    .toList());
        }
    }
}
