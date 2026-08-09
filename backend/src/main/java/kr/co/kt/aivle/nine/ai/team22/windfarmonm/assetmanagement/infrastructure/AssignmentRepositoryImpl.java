package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
    public List<Long> findUserIdsByWindFarmId(Long windFarmId) {
        return jpaRepository.findUserIdsByWindFarmId(windFarmId);
    }

    @Override
    public boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId) {
        return jpaRepository.existsByUserIdAndWindFarmId(userId, windFarmId);
    }

    @Override
    public boolean existsByUserIdAndTurbineId(Long userId, Long turbineId) {
        return jpaRepository.existsByUserIdAndTurbineId(userId, turbineId);
    }

    @Override
    public boolean existsByUserIdAndBladeId(Long userId, Long bladeId) {
        return jpaRepository.existsByUserIdAndBladeId(userId, bladeId);
    }

    @Override
    public List<Assignment> findByUserIdIn(Collection<Long> userIds) {
        return jpaRepository.findByUserIdIn(userIds);
    }

    @Override
    public void deleteByUserIdAndWindFarmIdIn(Long userId, Collection<Long> windFarmIds) {
        jpaRepository.deleteByUserIdAndWindFarmIdIn(userId, windFarmIds);
    }

    @Override
    public void saveAll(List<Assignment> assignments) {
        jpaRepository.saveAll(assignments);
    }
}
