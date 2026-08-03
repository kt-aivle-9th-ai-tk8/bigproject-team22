package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Turbine;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TurbineRepositoryImpl implements TurbineRepository {

    private final TurbineJpaRepository jpaRepository;

    @Override
    public Optional<Turbine> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Turbine> findByWindFarmId(Long windFarmId) {
        return jpaRepository.findByWindFarmId(windFarmId);
    }
}
