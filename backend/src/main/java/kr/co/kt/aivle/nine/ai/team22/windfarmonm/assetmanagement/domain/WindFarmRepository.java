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

    /** 전체 단지(ADMIN 통합조회 등 "전체 열람" 스코프에서 사용). */
    List<WindFarm> findAll();
}
