package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.List;

/**
 * 사용자-단지 담당 배정 저장소 포트. 조회 인가(AssetAccessGuard)의 판정 근거를 제공한다.
 */
public interface AssignmentRepository {

    /** 사용자가 담당하는 단지 id 목록 */
    List<Long> findWindFarmIdsByUserId(Long userId);

    boolean existsByUserIdAndWindFarmId(Long userId, Long windFarmId);

    /** 터빈이 속한 단지를 사용자가 담당하는지(터빈 존재 여부와 무관하게 단일 질의로 판정). */
    boolean existsByUserIdAndTurbineId(Long userId, Long turbineId);

    /** 블레이드가 속한 단지를 사용자가 담당하는지(블레이드→터빈→단지를 단일 질의로 판정). */
    boolean existsByUserIdAndBladeId(Long userId, Long bladeId);
}
