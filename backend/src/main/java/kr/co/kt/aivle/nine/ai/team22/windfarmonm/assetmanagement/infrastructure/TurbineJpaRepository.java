package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Turbine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurbineJpaRepository extends JpaRepository<Turbine, Long> {

    List<Turbine> findByWindFarmId(Long windFarmId);
}
