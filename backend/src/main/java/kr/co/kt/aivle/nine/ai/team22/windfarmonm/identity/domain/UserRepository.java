package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;


import java.util.List;
import java.util.Optional;

/**
 * 도메인 관점의 사용자 저장소 포트. 구현(어댑터)은 infra 레이어에 둔다.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<User> findAll();
}
