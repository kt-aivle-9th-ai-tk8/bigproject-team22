package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;


import java.util.List;
import java.util.Optional;

/**
 * 도메인 관점의 사용자 저장소 포트
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /** 사용자 존재 여부. assetmanagement의 담당 배정 시 실존 검증에 사용 */
    boolean existsById(Long id);

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<User> findAll();

    /** 특정 역할의 사용자 id 목록(알림 fan-out 의 ADMIN 수신자 조회 등). */
    List<Long> findUserIdsByRole(Role role);
}
