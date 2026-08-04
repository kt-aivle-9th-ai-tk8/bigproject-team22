package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.UserWindFarmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssignmentJpaRepository extends JpaRepository<Assignment, UserWindFarmId> {

    // get only wind_farm_id
    @Query("select a.windFarmId from Assignment a where a.userId = :userId")
    List<Long> findWindFarmIdsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId);

    /** 터빈 → 소속 단지 → 담당 배정을 한 번에 확인(터빈 존재 여부를 드러내지 않기 위해 인가를 선행 검사). */
    @Query("""
            select count(a) > 0 from Assignment a, Turbine t
            where t.id = :turbineId and a.userId = :userId and a.windFarmId = t.windFarmId
            """)
    boolean existsByUserIdAndTurbineId(@Param("userId") Long userId, @Param("turbineId") Long turbineId);

    /** 블레이드 → 터빈 → 소속 단지 → 담당 배정을 한 번에 확인. */
    @Query("""
            select count(a) > 0 from Assignment a, Turbine t, Blade b
            where b.id = :bladeId and t.id = b.turbineId
              and a.userId = :userId and a.windFarmId = t.windFarmId
            """)
    boolean existsByUserIdAndBladeId(@Param("userId") Long userId, @Param("bladeId") Long bladeId);
}
