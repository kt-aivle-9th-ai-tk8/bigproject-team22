package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WindFarmJpaRepository extends JpaRepository<WindFarm, Long> {
}
