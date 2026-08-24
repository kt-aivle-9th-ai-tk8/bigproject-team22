package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;


import java.util.List;
import java.util.Optional;

/**
 * 도메인 관점의 사용자 저장소 포트
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /**
     * 쓰기 잠금(PESSIMISTIC_WRITE)으로 조회한다. 조회한 상태를 근거로 파괴적 변경을 하는 경로가
     * 다른 트랜잭션과 직렬화되도록 쓴다 — 잠금 없이는 "GUEST 확인 → 삭제" 사이에 승인(changeRole)이
     * 끼어들어 승인된 계정이 지워질 수 있다.
     */
    Optional<User> findByIdForUpdate(Long id);

    /** 사용자 존재 여부. assetmanagement의 담당 배정 시 실존 검증에 사용 */
    boolean existsById(Long id);

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<User> findAll();

    /** 특정 역할의 사용자 id 목록(알림 fan-out 의 ADMIN 수신자 조회 등). */
    List<Long> findUserIdsByRole(Role role);

    /** 계정 삭제(가입 거절). 남긴 데이터가 있으면 FK 제약으로 실패한다 — 호출측이 번역한다. */
    void delete(User user);

    /** 보류된 쓰기를 DB 로 내보낸다. FK 위반을 커밋 이전에 잡아 번역하기 위해 쓴다. */
    void flush();
}
