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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** 단일 터빈의 발전량 요약(현재/당일/당월). */
    public PowerSummary summaryByTurbine(Long turbineId) {
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
        Double current = sum(turbineIds.stream()
                .map(id -> scadaRecordRepository.findLatestByTurbineId(id)
                        .map(ScadaRecord::getPowerOutput)
                        .orElse(null))
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
        return switch (term) {
            case HOURLY -> aggregate(
                    scadaRecordRepository.findByTurbineIdAndTimeBetween(turbineId, start, end),
                    r -> term.truncate(r.getTime()), ScadaRecord::getPowerOutput, start, end, term);
            case DAILY -> aggregate(
                    dailyGenerationRepository.findByTurbineIdAndTimeBetween(turbineId, start, end),
                    r -> term.truncate(r.getTime()), DailyGeneration::getDailyPowerOutput, start, end, term);
            case MONTHLY -> aggregate(
                    monthlyGenerationRepository.findByTurbineIdAndTimeBetween(turbineId, start, end),
                    r -> term.truncate(r.getTime()), MonthlyGeneration::getMonthlyPowerOutput, start, end, term);
        };
    }

    /** 여러 터빈 합산(발전소 전체) 기간/집계단위별 발전량 시계열. */
    public List<PowerPoint> seriesByTurbines(List<Long> turbineIds, LocalDateTime start, LocalDateTime end, PowerTerm term) {
        if (turbineIds == null || turbineIds.isEmpty()) {
            return List.of();
        }
        return switch (term) {
            case HOURLY -> aggregate(
                    scadaRecordRepository.findByTurbineIdsAndTimeBetween(turbineIds, start, end),
                    r -> term.truncate(r.getTime()), ScadaRecord::getPowerOutput, start, end, term);
            case DAILY -> aggregate(
                    dailyGenerationRepository.findByTurbineIdsAndTimeBetween(turbineIds, start, end),
                    r -> term.truncate(r.getTime()), DailyGeneration::getDailyPowerOutput, start, end, term);
            case MONTHLY -> aggregate(
                    monthlyGenerationRepository.findByTurbineIdsAndTimeBetween(turbineIds, start, end),
                    r -> term.truncate(r.getTime()), MonthlyGeneration::getMonthlyPowerOutput, start, end, term);
        };
    }

    /**
     * 시간 버킷별로 값을 합산하되, [start, end] 전체를 term 단위 그리드로 채워 연속 시계열을 만든다.
     * 데이터가 없는(또는 값이 null 인) 버킷은 power=null 로 반환해, FE 가 "빈 구간"을 인지하고
     * 그래프를 끊김 없이 그릴 수 있게 한다(0 이 아닌 null 로 두어 '결측'과 '발전량 0' 을 구분).
     */
    private static <T> List<PowerPoint> aggregate(List<T> rows,
                                                  Function<T, LocalDateTime> keyFn,
                                                  Function<T, Double> valueFn,
                                                  LocalDateTime start,
                                                  LocalDateTime end,
                                                  PowerTerm term) {
        Map<LocalDateTime, Double> sums = new HashMap<>();
        for (T row : rows) {
            Double value = valueFn.apply(row);
            if (value == null) {
                continue;
            }
            sums.merge(keyFn.apply(row), value, Double::sum);
        }

        List<PowerPoint> series = new ArrayList<>();
        for (LocalDateTime cursor = term.truncate(start), last = term.truncate(end);
             !cursor.isAfter(last);
             cursor = term.next(cursor)) {
            series.add(new PowerPoint(cursor, sums.get(cursor))); // 없으면 null
        }
        return series;
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
