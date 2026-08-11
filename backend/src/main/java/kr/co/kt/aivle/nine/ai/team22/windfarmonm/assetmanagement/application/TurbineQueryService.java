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
        // 인가 검사를 존재 확인보다 먼저 수행한다(미담당 사용자에게 404/403 차이로 터빈 존재를 노출하지 않도록).
        accessGuard.checkTurbineAccessById(userId, admin, turbineId);
        Turbine turbine = turbineRepository.findById(turbineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TURBINE_NOT_FOUND));

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
     * 터빈 코드를 조회한다(예: {@code U2}). <b>인가를 하지 않는</b> 내부 조회이므로,
     * 호출측이 담당 인가를 이미 통과한 맥락에서만 쓸 것(예: 생성된 보고서의 대상 표시·제목 조립).
     *
     * @return 터빈 코드, 없으면 null
     */
    @Transactional(readOnly = true)
    public String getTurbineCode(Long turbineId) {
        return turbineRepository.findById(turbineId).map(Turbine::getCode).orElse(null);
    }

    /**
     * 터빈의 소속 단지 id 를 조회한다. <b>인가를 하지 않는</b> 내부 조회이므로 호출측 인가 선행을 전제한다
     * (예: 점검 생성 시 결함 보고서의 wind_farm_id 채움).
     *
     * @return 소속 단지 id, 터빈이 없으면 null
     */
    @Transactional(readOnly = true)
    public Long getWindFarmId(Long turbineId) {
        return turbineRepository.findById(turbineId).map(Turbine::getWindFarmId).orElse(null);
    }

    /**
     * 터빈 발전량 시계열 조회.
     */
    @Transactional(readOnly = true)
    public List<PowerPoint> getTurbinePower(Long userId, boolean admin, Long turbineId, PowerQuery query) {
        // 인가 → 존재 확인 순서(getTurbine 과 동일 정책).
        accessGuard.checkTurbineAccessById(userId, admin, turbineId);
        turbineRepository.findById(turbineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TURBINE_NOT_FOUND));
        return powerQueryService.seriesByTurbine(turbineId, query.start(), query.end(), query.term());
    }
}
