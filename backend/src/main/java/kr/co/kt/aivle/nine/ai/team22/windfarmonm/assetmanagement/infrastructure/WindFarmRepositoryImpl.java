package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarm;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WindFarmRepositoryImpl implements WindFarmRepository {

    private final WindFarmJpaRepository jpaRepository;

    @Override
    public Optional<WindFarm> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<WindFarm> findAllByIdIn(Collection<Long> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public List<WindFarm> findAll() {
        return jpaRepository.findAll();
    }
}
