package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * TurbineModel 저장소 포트.
 */
public interface TurbineModelRepository {

    Optional<TurbineModel> findById(Long id);

    List<TurbineModel> findAllByIdIn(Collection<Long> ids);
}
