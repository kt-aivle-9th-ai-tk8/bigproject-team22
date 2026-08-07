package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port;

import java.util.List;

/**
 * 보고서가 필요로 하는 자산(assetmanagement) 조회 포트. 소비자(maintenancereporting)가 소유한다.
 * <p>
 * 자산의 존재·소속 정합성은 이 포트가 아니라 DB 제약(단일 FK + 복합 FK)이 강제한다. 따라서 여기에는
 * 담당 인가 판정만 둔다.
 */
public interface ReportAssetPort {

    /**
     * 사용자가 열람할 수 있는 단지 목록.
     *
     * @return ADMIN 이면 {@code null}(제한 없음). 그 외에는 담당 단지 id 목록이며, 담당이 없으면 빈 목록.
     */
    List<Long> viewableWindFarmIds(Long userId, boolean admin);
}
