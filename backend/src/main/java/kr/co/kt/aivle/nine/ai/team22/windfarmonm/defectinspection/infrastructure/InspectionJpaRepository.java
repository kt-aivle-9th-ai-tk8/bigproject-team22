package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import jakarta.persistence.LockModeType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InspectionJpaRepository extends JpaRepository<Inspection, Long> {

    /** SELECT ... FOR UPDATE — 상태 전이 경로의 동시 요청 직렬화용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inspection> findWithLockById(Long id);
}
