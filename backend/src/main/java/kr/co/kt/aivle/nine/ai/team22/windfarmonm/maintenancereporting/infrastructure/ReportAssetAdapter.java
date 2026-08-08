package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssetAccessGuard;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.TurbineQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.WindFarmQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportTargetInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportAssetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ReportAssetPort} 어댑터. assetmanagement BC 의 application 서비스로만 위임한다
 * (assetmanagement 는 maintenancereporting 을 알지 못한다 — 의존은 단방향이다).
 */
@Component
@RequiredArgsConstructor
public class ReportAssetAdapter implements ReportAssetPort {

    private final AssetAccessGuard accessGuard;
    private final TurbineQueryService turbineQueryService;
    private final WindFarmQueryService windFarmQueryService;

    @Override
    public List<Long> viewableWindFarmIds(Long userId, boolean admin) {
        return accessGuard.viewableWindFarmIds(userId, admin);
    }

    @Override
    public ReportTargetInfo resolveTargetInfo(Long windFarmId, Long turbineId) {
        // 인가 없는 내부 조회 — 생성 시점에 이미 담당 인가를 통과한 보고서의 대상이라 재인가하지 않는다.
        String windFarmName = windFarmId == null ? null : windFarmQueryService.getWindFarmName(windFarmId);
        String turbineCode = turbineId == null ? null : turbineQueryService.getTurbineCode(turbineId);
        return new ReportTargetInfo(windFarmName, turbineCode);
    }
}
