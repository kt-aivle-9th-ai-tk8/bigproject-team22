package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerPoint;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerQuery;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerSummary;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.TurbineSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WindFarmDetailResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WindFarmSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.port.WeatherProvider;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Turbine;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModel;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineModelRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarm;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WindFarmRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 풍력단지 조회 유스케이스(담당 단지 통합조회, 단일 단지 상세, 단지 발전량 시계열).
 */
@Service
@RequiredArgsConstructor
public class WindFarmQueryService {

    private final WindFarmRepository windFarmRepository;
    private final TurbineRepository turbineRepository;
    private final TurbineModelRepository turbineModelRepository;
    private final PowerQueryService powerQueryService;
    private final WeatherProvider weatherProvider;
    private final AssetAccessGuard accessGuard;

    /**
     * 담당 풍력단지 통합조회. 담당(Assignment) 단지만 대상으로 하며,
     * location/weather/power 플래그로 각 필드 포함 여부를 제어한다.
     * top-n 이 지정되면 단지 용량(capacity) 기준 상위 n개만 반환한다.
     */
    @Transactional(readOnly = true)
    public List<WindFarmSummaryResult> getWindFarms(Long userId, Integer topN,
                                                    boolean location, boolean weather, boolean power) {
        List<Long> assignedIds = accessGuard.assignedWindFarmIds(userId);
        if (assignedIds.isEmpty()) {
            return List.of();
        }
        List<WindFarm> windFarms = new ArrayList<>(windFarmRepository.findAllByIdIn(assignedIds));

        // top-n 은 단지 용량(capacity) 기준 상위 n개. capacity 미상(null)은 뒤로 정렬.
        windFarms.sort(Comparator.comparingDouble(
                (WindFarm wf) -> wf.getCapacity() == null ? Double.NEGATIVE_INFINITY : wf.getCapacity()).reversed());
        if (topN != null && topN >= 0 && windFarms.size() > topN) {
            windFarms = windFarms.subList(0, topN);
        }

        // 상위 n개로 좁힌 뒤에만 발전량/날씨를 계산(불필요한 조회 방지).
        return windFarms.stream()
                .map(wf -> new WindFarmSummaryResult(
                        wf.getId(),
                        wf.getName(),
                        location ? wf.getLatitude() : null,
                        location ? wf.getLongitude() : null,
                        wf.getCapacity(),
                        weather ? weatherProvider.getWeather(wf.getAwsStationId(), wf.getAsosStationId()) : null,
                        power ? powerQueryService.summaryByTurbines(turbineIdsOf(wf.getId())) : null))
                .toList();
    }

    /**
     * 단일 풍력단지 상세조회. 제원/날씨/발전량/터빈 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public WindFarmDetailResult getWindFarm(Long userId, boolean admin, Long windFarmId) {
        WindFarm windFarm = windFarmRepository.findById(windFarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND));
        accessGuard.checkWindFarmAccess(userId, admin, windFarmId);

        List<Turbine> turbines = turbineRepository.findByWindFarmId(windFarmId);
        Map<Long, String> modelNames = modelNamesOf(turbines);
        List<TurbineSummaryResult> turbineResults = turbines.stream()
                .map(t -> new TurbineSummaryResult(
                        t.getId(),
                        t.getLatitude(),
                        t.getLongitude(),
                        modelNames.get(t.getTurbineModelId()),
                        t.getCode()))
                .toList();

        List<Long> turbineIds = turbines.stream().map(Turbine::getId).toList();
        PowerSummary power = powerQueryService.summaryByTurbines(turbineIds);
        WeatherInfo weather = weatherProvider.getWeather(windFarm.getAwsStationId(), windFarm.getAsosStationId());

        return new WindFarmDetailResult(
                windFarm.getId(),
                windFarm.getName(),
                windFarm.getCapacity(),
                weather,
                power,
                turbineResults
        );
    }

    /**
     * 풍력단지 발전량 시계열 조회(단지 소속 터빈 합산).
     */
    @Transactional(readOnly = true)
    public List<PowerPoint> getWindFarmPower(Long userId, boolean admin, Long windFarmId, PowerQuery query) {
        windFarmRepository.findById(windFarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND));
        accessGuard.checkWindFarmAccess(userId, admin, windFarmId);
        List<Long> turbineIds = turbineIdsOf(windFarmId);
        return powerQueryService.seriesByTurbines(turbineIds, query.start(), query.end(), query.term());
    }

    private List<Long> turbineIdsOf(Long windFarmId) {
        return turbineRepository.findByWindFarmId(windFarmId).stream()
                .map(Turbine::getId)
                .toList();
    }

    private Map<Long, String> modelNamesOf(List<Turbine> turbines) {
        List<Long> modelIds = turbines.stream().map(Turbine::getTurbineModelId).distinct().toList();
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return turbineModelRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(TurbineModel::getId, TurbineModel::getModel));
    }
}
