package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.List;
import java.util.Optional;

/**
 * Turbine 저장소 포트.
 */
public interface TurbineRepository {

    Optional<Turbine> findById(Long id);

    List<Turbine> findByWindFarmId(Long windFarmId);
}
