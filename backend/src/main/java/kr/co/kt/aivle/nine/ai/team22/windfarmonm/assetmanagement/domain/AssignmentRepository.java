package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.Collection;
import java.util.List;

/**
 * 사용자-단지 담당 배정 저장소 포트. 조회 인가(AssetAccessGuard)의 판정 근거를 제공하고,
 * 관리자 화면의 담당 배정 조회/변경을 지원한다.
 */
public interface AssignmentRepository {

    /** 사용자가 담당하는 단지 id 목록 */
    List<Long> findWindFarmIdsByUserId(Long userId);

    /** 여러 사용자의 배정을 한 번에 조회(관리자 목록 조회 N+1 방지). */
    List<Assignment> findByUserIdIn(Collection<Long> userIds);

    /** 사용자의 특정 단지 배정만 삭제(전체 교체 시 차집합 삭제용). */
    void deleteByUserIdAndWindFarmIdIn(Long userId, Collection<Long> windFarmIds);

    /** 담당 배정들을 저장한다. */
    void saveAll(List<Assignment> assignments);

    boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId);

    /** 터빈이 속한 단지를 사용자가 담당하는지(터빈 존재 여부와 무관하게 단일 질의로 판정). */
    boolean existsByUserIdAndTurbineId(Long userId, Long turbineId);

    /** 블레이드가 속한 단지를 사용자가 담당하는지(블레이드→터빈→단지를 단일 질의로 판정). */
    boolean existsByUserIdAndBladeId(Long userId, Long bladeId);
}
