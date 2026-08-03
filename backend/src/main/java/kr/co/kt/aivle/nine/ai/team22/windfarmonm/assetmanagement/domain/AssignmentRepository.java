package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.List;

/**
 * 사용자-단지 담당 배정 저장소 포트.
 */
public interface AssignmentRepository {

    /** 사용자가 담당하는 단지 id 목록 */
    List<Long> findWindFarmIdsByUserId(Long userId);

    boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId);

    /** 사용자의 기존 담당 배정을 모두 삭제한다(전체 교체 시 선행). */
    void deleteByUserId(Long userId);

    /** 담당 배정들을 저장한다. */
    void saveAll(List<Assignment> assignments);
}
