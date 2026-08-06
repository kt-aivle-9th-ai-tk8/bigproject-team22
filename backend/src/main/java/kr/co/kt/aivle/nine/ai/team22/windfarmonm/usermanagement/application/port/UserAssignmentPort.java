package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 담당 단지 배정(assetmanagement BC) 접근 포트. 소비자(usermanagement)가 소유한다.
 */
public interface UserAssignmentPort {

    /** 여러 사용자의 담당 단지를 한 번에 조회(N+1 방지). 배정 없는 사용자는 키가 없다. */
    Map<Long, List<AssignedWindFarm>> findByUserIds(Collection<Long> userIds);

    /** 단일 사용자의 담당 단지(없으면 빈 목록). */
    List<AssignedWindFarm> findByUserId(Long userId);

    /** 담당 단지를 요청 목록으로 전체 교체한다(빈 목록 = 전체 해제). */
    void replaceAssignments(Long userId, List<Long> windFarmIds);

    /** 배정된 단지(식별자 + 표시명). */
    record AssignedWindFarm(Long windFarmId, String windFarmName) {
    }
}
