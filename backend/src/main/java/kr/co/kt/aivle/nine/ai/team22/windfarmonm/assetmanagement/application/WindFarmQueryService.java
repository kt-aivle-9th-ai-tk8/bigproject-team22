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
import java.util.HashMap;
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
     * 풍력단지 통합조회. 열람 범위는 {@link AssetAccessGuard#viewableWindFarmIds(Long, boolean)} 규약을 따른다
     * — ADMIN 은 전체 단지, 그 외 사용자는 담당(Assignment) 단지만 대상으로 한다.
     * location/weather/power 플래그로 각 필드 포함 여부를 제어하고,
     * top-n 이 지정되면 단지 용량(capacity) 기준 상위 n개만 반환한다.
     */
    @Transactional(readOnly = true)
    public List<WindFarmSummaryResult> getWindFarms(Long userId, boolean admin, Integer topN,
                                                    boolean location, boolean weather, boolean power) {
        // 입력 검증은 조기 반환보다 먼저 — 담당 단지 유무에 따라 같은 요청이 400/200 으로 갈리지 않도록.
        if (topN != null && topN <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<Long> viewableIds = accessGuard.viewableWindFarmIds(userId, admin);
        if (viewableIds != null && viewableIds.isEmpty()) {
            return List.of(); // 담당 단지가 없는 비-ADMIN 사용자
        }
        // viewableIds == null → ADMIN(전체 열람), 그 외 → 담당 단지로 한정.
        List<WindFarm> windFarms = new ArrayList<>(
                viewableIds == null ? windFarmRepository.findAll() : windFarmRepository.findAllByIdIn(viewableIds));

        // top-n 은 단지 용량(capacity) 기준 상위 n개. capacity 미상(null)은 뒤로 정렬.
        // 동률(특히 capacity 가 전부 null 인 초기 데이터)에서 결과가 호출마다 달라지지 않도록 id 를 2차 키로 둔다.
        windFarms.sort(Comparator
                .comparingDouble((WindFarm wf) -> wf.getCapacity() == null ? Double.NEGATIVE_INFINITY : wf.getCapacity())
                .reversed()
                .thenComparing(WindFarm::getId));
        if (topN != null && windFarms.size() > topN) {
            windFarms = windFarms.subList(0, topN);
        }

        // 상위 n개로 좁힌 뒤에만 발전량/날씨를 계산(불필요한 조회 방지).
        // N+1 방지: 터빈은 대상 단지들을 한 번에 조회해 단지별로 그룹화하고,
        // 날씨는 관측소 ID 기준 중복 제거 후 지점당 1회만 조회한다.
        List<Long> farmIds = windFarms.stream().map(WindFarm::getId).toList();
        Map<Long, List<Long>> turbineIdsByFarm = power
                ? turbineRepository.findByWindFarmIdIn(farmIds).stream()
                        .collect(Collectors.groupingBy(Turbine::getWindFarmId,
                                Collectors.mapping(Turbine::getId, Collectors.toList())))
                : Map.of();
        Map<Long, WeatherInfo> weatherByStation = new HashMap<>();
        if (weather) {
            windFarms.stream().map(WindFarm::getAsosStationId).distinct()
                    .forEach(stationId -> weatherByStation.put(stationId, weatherProvider.getWeather(stationId)));
        }

        return windFarms.stream()
                .map(wf -> new WindFarmSummaryResult(
                        wf.getId(),
                        wf.getName(),
                        location ? wf.getLatitude() : null,
                        location ? wf.getLongitude() : null,
                        wf.getCapacity(),
                        weather ? weatherByStation.get(wf.getAsosStationId()) : null,
                        power ? powerQueryService.summaryByTurbines(
                                turbineIdsByFarm.getOrDefault(wf.getId(), List.of())) : null))
                .toList();
    }

    /**
     * 단일 풍력단지 상세조회. 제원/날씨/발전량/터빈 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public WindFarmDetailResult getWindFarm(Long userId, boolean admin, Long windFarmId) {
        // 인가 검사를 존재 확인보다 먼저 수행한다(미배정 사용자에게 404/403 차이로 단지 존재를 노출하지 않도록).
        accessGuard.checkWindFarmAccess(userId, admin, windFarmId);
        WindFarm windFarm = windFarmRepository.findById(windFarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND));

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
        WeatherInfo weather = weatherProvider.getWeather(windFarm.getAsosStationId());

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
        accessGuard.checkWindFarmAccess(userId, admin, windFarmId);
        windFarmRepository.findById(windFarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND));
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
