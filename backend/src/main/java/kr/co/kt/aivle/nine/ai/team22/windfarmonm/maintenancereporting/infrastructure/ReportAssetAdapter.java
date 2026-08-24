package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssetAccessGuard;
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

    @Override
    public List<Long> viewableWindFarmIds(Long userId, boolean admin) {
        return accessGuard.viewableWindFarmIds(userId, admin);
    }
}
