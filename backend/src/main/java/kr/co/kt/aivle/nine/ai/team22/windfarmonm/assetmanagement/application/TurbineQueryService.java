package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.BladeSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerPoint;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerQuery;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerSummary;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.TurbineDetailResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.BladeRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Turbine;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModel;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModelRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 터빈 조회 유스케이스(3D 뷰어 통합조회, 발전량 시계열).
 */
@Service
@RequiredArgsConstructor
public class TurbineQueryService {

    private final TurbineRepository turbineRepository;
    private final TurbineModelRepository turbineModelRepository;
    private final BladeRepository bladeRepository;
    private final PowerQueryService powerQueryService;
    private final AssetAccessGuard accessGuard;

    /**
     * 터빈 3D 뷰어 통합조회. 제원/좌표/최신 발전량/블레이드 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public TurbineDetailResult getTurbine(Long userId, boolean admin, Long turbineId) {
        Turbine turbine = turbineRepository.findById(turbineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TURBINE_NOT_FOUND));
        accessGuard.checkTurbineAccess(userId, admin, turbine.getWindFarmId());

        String model = turbineModelRepository.findById(turbine.getTurbineModelId())
                .map(TurbineModel::getModel)
                .orElse(null);

        List<BladeSummaryResult> blades = bladeRepository.findByTurbineId(turbineId).stream()
                .map(BladeSummaryResult::from)
                .toList();

        PowerSummary power = powerQueryService.summaryByTurbine(turbineId);

        return new TurbineDetailResult(
                turbine.getId(),
                turbine.getLatitude(),
                turbine.getLongitude(),
                model,
                turbine.getCode(),
                power,
                blades
        );
    }

    /**
     * 터빈 발전량 시계열 조회.
     */
    @Transactional(readOnly = true)
    public List<PowerPoint> getTurbinePower(Long userId, boolean admin, Long turbineId, PowerQuery query) {
        Turbine turbine = turbineRepository.findById(turbineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TURBINE_NOT_FOUND));
        accessGuard.checkTurbineAccess(userId, admin, turbine.getWindFarmId());
        return powerQueryService.seriesByTurbine(turbineId, query.start(), query.end(), query.term());
    }
}
