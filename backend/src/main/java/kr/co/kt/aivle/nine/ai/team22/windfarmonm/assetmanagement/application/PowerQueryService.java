package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerPoint;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerSummary;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerTerm;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.DailyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.DailyGenerationRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.MonthlyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.MonthlyGenerationRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 발전량 조회 유스케이스. term 에 따라 RDS 를 직접 질의한다. 세 테이블 모두 시각을
 * LocalDateTime(time) 으로 통일 매핑하며(집계는 일/월 단위로 절삭 저장), 조회/집계 로직이 대칭이다.
 * <ul>
 *   <li>HOURLY  → scada_record(raw power_output)를 시간 단위로 집계</li>
 *   <li>DAILY   → daily_generation(daily_power_output)</li>
 *   <li>MONTHLY → monthly_generation(monthly_power_output)</li>
 * </ul>
 * 요약(current/today/month)은 세 값 모두 <b>조회할 시각을 지정</b>해 PK 로 직접 읽는다 —
 * 현재출력=scada_record 의 직전 정시(없으면 한 시간 전으로 1단계 폴백), 당일=daily_generation 의 당일 00:00,
 * 당월=monthly_generation 의 당월 1일 00:00. 단지 발전량은 소속 터빈들의 값을 합산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PowerQueryService {

    private final ScadaRecordRepository scadaRecordRepository;
    private final DailyGenerationRepository dailyGenerationRepository;
    private final MonthlyGenerationRepository monthlyGenerationRepository;

    /** 발전량 집계 배치와 동일한 기준 시간대(당일/당월 키 조회). 서버 기본 시간대 의존을 피한다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 정시 직후 적재 배치가 끝나지 않았을 때 되돌아볼 시간(정시 단위). */
    private static final int LOOKBACK_HOURS = 1;

    /** 단일 터빈의 발전량 요약(현재/당일/당월). */
    public PowerSummary summaryByTurbine(Long turbineId) {
        LocalDate today = LocalDate.now(ZONE);
        Double current = currentPower(List.of(turbineId));
        Double todayPower = dailyGenerationRepository.findByTurbineIdAndTime(turbineId, today.atStartOfDay())
                .map(DailyGeneration::getDailyPowerOutput)
                .orElse(null);
        Double monthPower = monthlyGenerationRepository.findByTurbineIdAndTime(turbineId, firstOfMonth(today))
                .map(MonthlyGeneration::getMonthlyPowerOutput)
                .orElse(null);
        return new PowerSummary(current, todayPower, monthPower);
    }

    /** 여러 터빈 합산(발전소 전체) 발전량 요약. */
    public PowerSummary summaryByTurbines(List<Long> turbineIds) {
        if (turbineIds == null || turbineIds.isEmpty()) {
            return PowerSummary.empty();
        }
        LocalDate today = LocalDate.now(ZONE);
        Double current = currentPower(turbineIds);
        Double todayPower = sum(dailyGenerationRepository.findByTurbineIdsAndTime(turbineIds, today.atStartOfDay()).stream()
                .map(DailyGeneration::getDailyPowerOutput)
                .toList());
        Double monthPower = sum(monthlyGenerationRepository.findByTurbineIdsAndTime(turbineIds, firstOfMonth(today)).stream()
                .map(MonthlyGeneration::getMonthlyPowerOutput)
                .toList());
        return new PowerSummary(current, todayPower, monthPower);
    }

    /** 단일 터빈의 기간/집계단위별 발전량 시계열. */
    public List<PowerPoint> seriesByTurbine(Long turbineId, LocalDateTime start, LocalDateTime end, PowerTerm term) {
        LocalDateTime from = term.truncate(start); // 조회 하한만 절삭(집계행은 버킷 시작 시각에 저장되므로)
        return switch (term) {
            case HOURLY -> aggregate(
                    scadaRecordRepository.findByTurbineIdAndTimeBetween(turbineId, from, end),
                    r -> term.truncate(r.getTime()), ScadaRecord::getPowerOutput);
            case DAILY -> aggregate(
                    dailyGenerationRepository.findByTurbineIdAndTimeBetween(turbineId, from, end),
                    r -> term.truncate(r.getTime()), DailyGeneration::getDailyPowerOutput);
            case MONTHLY -> aggregate(
                    monthlyGenerationRepository.findByTurbineIdAndTimeBetween(turbineId, from, end),
                    r -> term.truncate(r.getTime()), MonthlyGeneration::getMonthlyPowerOutput);
        };
    }

    /** 여러 터빈 합산(발전소 전체) 기간/집계단위별 발전량 시계열. */
    public List<PowerPoint> seriesByTurbines(List<Long> turbineIds, LocalDateTime start, LocalDateTime end, PowerTerm term) {
        if (turbineIds == null || turbineIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime from = term.truncate(start); // 조회 하한만 절삭(집계행은 버킷 시작 시각에 저장되므로)
        return switch (term) {
            case HOURLY -> aggregate(
                    scadaRecordRepository.findByTurbineIdsAndTimeBetween(turbineIds, from, end),
                    r -> term.truncate(r.getTime()), ScadaRecord::getPowerOutput);
            case DAILY -> aggregate(
                    dailyGenerationRepository.findByTurbineIdsAndTimeBetween(turbineIds, from, end),
                    r -> term.truncate(r.getTime()), DailyGeneration::getDailyPowerOutput);
            case MONTHLY -> aggregate(
                    monthlyGenerationRepository.findByTurbineIdsAndTimeBetween(turbineIds, from, end),
                    r -> term.truncate(r.getTime()), MonthlyGeneration::getMonthlyPowerOutput);
        };
    }

    /**
     * 시간 버킷별로 터빈 값을 합산해 시각 오름차순 시계열을 만든다.
     * <p>
     * <b>빈 구간 채우기(그리드 생성)는 하지 않는다.</b> SCADA 적재기가 결측 시각도 null 값 행으로 채우도록
     * 설계되어 있으므로, 저장된 행을 그대로(값이 null 이면 null 인 채로) 반환하는 것이 원천과 일치한다.
     * 값이 null 인 버킷도 유지해 FE 가 '결측'과 '발전량 0' 을 구분할 수 있게 한다.
     * 같은 버킷에 여러 터빈이 있으면 null 을 무시하고 합산하되, 전부 null 이면 null 로 남긴다.
     */
    private static <T> List<PowerPoint> aggregate(List<T> rows,
                                                  Function<T, LocalDateTime> keyFn,
                                                  Function<T, Double> valueFn) {
        Map<LocalDateTime, Double> sums = new TreeMap<>(); // 시각 오름차순 유지
        for (T row : rows) {
            LocalDateTime bucket = keyFn.apply(row);
            Double value = valueFn.apply(row);
            if (!sums.containsKey(bucket)) {
                sums.put(bucket, value); // null 이어도 버킷은 남긴다(결측 표시)
            } else if (value != null) {
                Double prev = sums.get(bucket);
                sums.put(bucket, prev == null ? value : prev + value);
            }
        }
        return sums.entrySet().stream()
                .map(e -> new PowerPoint(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 터빈들의 '현재 출력' 합산. 가장 마지막 행을 찾지 않고 <b>조회 시각을 지정</b>한다.
     * <p>
     * 기준은 직전 정시({@code now} 절삭)이며, 해당 행이 없으면 한 시간 전으로 1단계만 폴백한다.
     * 정시 직후에는 적재 배치가 아직 끝나지 않았을 수 있기 때문이다. 폴백은 <b>터빈별로</b> 독립 적용해
     * 일부 터빈만 적재된 상황에서도 가용한 값을 최대한 사용한다.
     * <p>
     * 구분에 유의: <b>행은 있는데 값이 null</b> 이면 계측 결측이므로 그 행을 그대로 쓴다(폴백하지 않는다).
     * <b>행 자체가 없을 때만</b> 폴백하며, 두 시각 모두 없으면 적재 배치 이상 신호이므로 경고 로그를 남긴다
     * (응답 계약은 유지 — 사용자에게는 값 없음(null)으로 나간다).
     */
    private Double currentPower(List<Long> turbineIds) {
        LocalDateTime at = LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.HOURS);
        LocalDateTime fallback = at.minusHours(LOOKBACK_HOURS);

        Map<Long, ScadaRecord> newestByTurbine = new HashMap<>();
        for (ScadaRecord record : scadaRecordRepository.findByTurbineIdsAndTimeIn(turbineIds, List.of(at, fallback))) {
            newestByTurbine.merge(record.getTurbineId(), record,
                    (kept, candidate) -> candidate.getTime().isAfter(kept.getTime()) ? candidate : kept);
        }

        int missing = turbineIds.size() - newestByTurbine.size();
        if (missing > 0) {
            log.warn("SCADA 미적재 터빈 {}/{}개(기준 {}, 폴백 {}). 적재 배치 동작을 확인할 것.",
                    missing, turbineIds.size(), at, fallback);
        }
        return sum(newestByTurbine.values().stream()
                .map(ScadaRecord::getPowerOutput)
                .toList());
    }

    /** null 을 무시하고 합산한다. 유효 값이 하나도 없으면 null 을 반환한다. */
    private static Double sum(List<Double> values) {
        Double result = null;
        for (Double value : values) {
            if (value != null) {
                result = (result == null) ? value : result + value;
            }
        }
        return result;
    }

    /** 해당 월 1일 00:00. 월별 집계(monthly_generation)의 time 키와 맞춘다. */
    private static LocalDateTime firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1).atStartOfDay();
    }
}
