package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.List;
import java.util.Optional;

/**
 * Blade 저장소 포트.
 */
public interface BladeRepository {

    Optional<Blade> findById(Long id);

    List<Blade> findByTurbineId(Long turbineId);
}
