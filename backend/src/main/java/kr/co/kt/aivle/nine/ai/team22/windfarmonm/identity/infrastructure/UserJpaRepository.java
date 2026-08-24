package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import jakarta.persistence.LockModeType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    @Query("select u.id from User u where u.role = :role")
    List<Long> findUserIdsByRole(@Param("role") Role role);

    /** SELECT ... FOR UPDATE — 상태 확인 후 파괴적 변경을 하는 경로의 직렬화용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);
}
