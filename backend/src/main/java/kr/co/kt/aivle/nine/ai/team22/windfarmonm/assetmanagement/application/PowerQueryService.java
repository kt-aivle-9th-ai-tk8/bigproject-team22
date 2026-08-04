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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 요약(current/today/month)은 현재출력=scada 최신값, 당일=daily_generation, 당월=monthly_generation 을 사용한다.
 * 단지 발전량은 소속 터빈들의 값을 합산한다.
 */
@Service
@RequiredArgsConstructor
public class PowerQueryService {

    private final ScadaRecordRepository scadaRecordRepository;
    private final DailyGenerationRepository dailyGenerationRepository;
    private final MonthlyGenerationRepository monthlyGenerationRepository;

    /** 발전량 집계 배치와 동일한 기준 시간대(당일/당월 키 조회). 서버 기본 시간대 의존을 피한다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 단일 터빈의 발전량 요약(현재/당일/당월). */
    public PowerSummary summaryByTurbine(Long turbineId) {
        LocalDate today = LocalDate.now(ZONE);
        Double current = scadaRecordRepository.findLatestByTurbineId(turbineId)
                .map(ScadaRecord::getPowerOutput)
                .orElse(null);
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
        // TODO(성능): findLatestByTurbineIds 는 대상 터빈의 전 이력을 훑는다(ScadaRecordJpaRepository 주석 참조).
        //  또한 이 메서드가 단지마다 호출되어 단지 수 × 3쿼리(최신/일/월)가 나간다. 통합조회는 전체 터빈 id 를
        //  한 번에 모아 3쿼리로 끝내고 단지별로 합산하는 배치 조회로 정리할 것.
        Double current = sum(scadaRecordRepository.findLatestByTurbineIds(turbineIds).stream()
                .map(ScadaRecord::getPowerOutput)
                .toList());
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
