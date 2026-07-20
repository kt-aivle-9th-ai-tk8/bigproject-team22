package kr.co.kt.aivle.nine.ai.team22.windfarmonm.infra.user;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);
}
