package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private final AssignmentJpaRepository jpaRepository;

    @Override
    public List<Long> findWindFarmIdsByUserId(Long userId) {
        return jpaRepository.findWindFarmIdsByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId) {
        return jpaRepository.existsByUserIdAndWindFarmId(userId, windFarmId);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    @Override
    public void saveAll(List<Assignment> assignments) {
        jpaRepository.saveAll(assignments);
    }
}
