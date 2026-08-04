package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Blade;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.BladeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BladeRepositoryImpl implements BladeRepository {

    private final BladeJpaRepository jpaRepository;

    @Override
    public Optional<Blade> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Blade> findByTurbineId(Long turbineId) {
        return jpaRepository.findByTurbineId(turbineId);
    }
}
