package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssetAccessGuard;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.BladeQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.TurbineQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link InspectionAssetPort} 어댑터. assetmanagement BC 의 application 서비스로만 위임한다
 * (assetmanagement 는 defectinspection 을 알지 못한다 — 의존은 단방향이다).
 */
@Component
@RequiredArgsConstructor
public class InspectionAssetAdapter implements InspectionAssetPort {

    private final AssetAccessGuard accessGuard;
    private final TurbineQueryService turbineQueryService;
    private final BladeQueryService bladeQueryService;

    @Override
    public void checkWindFarmAccess(Long userId, boolean admin, Long windFarmId) {
        accessGuard.checkWindFarmAccess(userId, admin, windFarmId);
    }

    @Override
    public void checkTurbineAccess(Long userId, boolean admin, Long turbineId) {
        accessGuard.checkTurbineAccessById(userId, admin, turbineId);
    }

    @Override
    public Long windFarmIdOf(Long turbineId) {
        return turbineQueryService.getWindFarmId(turbineId);
    }

    @Override
    public void checkBladeAccess(Long userId, boolean admin, Long bladeId) {
        accessGuard.checkBladeAccessById(userId, admin, bladeId);
    }

    @Override
    public boolean bladeExists(Long bladeId) {
        return bladeQueryService.existsById(bladeId);
    }

    @Override
    public List<BladeRef> bladesOf(Long turbineId) {
        return bladeQueryService.getBladeIdentities(turbineId).stream()
                .map(blade -> new BladeRef(blade.id(), blade.tag()))
                .toList();
    }
}
