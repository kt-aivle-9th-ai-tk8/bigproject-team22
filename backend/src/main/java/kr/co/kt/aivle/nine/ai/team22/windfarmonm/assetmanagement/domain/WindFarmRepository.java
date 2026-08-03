package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * WindFarm 저장소 포트. 구현(어댑터)은 infrastructure 레이어에 둔다.
 */
public interface WindFarmRepository {

    Optional<WindFarm> findById(Long id);

    List<WindFarm> findAllByIdIn(Collection<Long> ids);
}
