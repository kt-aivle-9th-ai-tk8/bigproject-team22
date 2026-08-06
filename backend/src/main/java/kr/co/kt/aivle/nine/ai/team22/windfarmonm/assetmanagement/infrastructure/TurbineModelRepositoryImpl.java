package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModel;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TurbineModelRepositoryImpl implements TurbineModelRepository {

    private final TurbineModelJpaRepository jpaRepository;

    @Override
    public Optional<TurbineModel> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<TurbineModel> findAllByIdIn(Collection<Long> ids) {
        return jpaRepository.findAllById(ids);
    }
}
