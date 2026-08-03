package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Assignment;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.UserWindFarmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssignmentJpaRepository extends JpaRepository<Assignment, UserWindFarmId> {

    // get only user_id
    @Query("select a.windFarmId from Assignment a where a.userId = :userId")
    List<Long> findWindFarmIdsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId);

    void deleteByUserId(Long userId);
}
