package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Turbine 저장소 포트.
 */
public interface TurbineRepository {

    Optional<Turbine> findById(Long id);

    List<Turbine> findByWindFarmId(Long windFarmId);

    /** 여러 단지의 터빈을 한 번에 조회(통합조회 N+1 방지). */
    List<Turbine> findByWindFarmIdIn(Collection<Long> windFarmIds);
}
