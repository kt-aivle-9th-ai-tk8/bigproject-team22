package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WeatherType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 기상청 지상관측 시간자료(kma_sfctm2.php) 응답 파서.
 * <p>
 * 출력은 고정폭이지만 공백 분리 시 행당 46토큰으로 안정적이다(KMA 는 빈 문자열 필드를 "-" 로 채우므로
 * 토큰이 붕괴되지 않는다). 사용 토큰(0-base): 1 STN, 3 WS(풍속 m/s), 11 TA(기온 °C),
 * 24 WW(국내식 일기코드). WW 는 {@link WeatherType#from(String)} 으로 날씨유형에 매핑한다.
 * '#' 라인/12자리 시각으로 시작하지 않는 라인은 건너뛴다.
 * <p>
 * 결측값: 기온(TA)은 음의 정상값이 있으므로 ≤ -50 → null, 풍속(WS)은 음수(-9 등) → null.
 * 재사용 메모: 라인 스트리핑/결측 정규화는 {@code KmaAwsResponseParser} 와 공통화 여지가 있다.
 */
final class KmaAsosResponseParser {

    private static final DateTimeFormatter TM = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int MIN_TOKENS = 25; // index 24(WW) 안전 접근 보장

    private KmaAsosResponseParser() {
    }

    /** ASOS 관측 1건(파싱 결과). 결측값은 null, WW 미매핑/빈값은 CLEAR. */
    record Reading(Long stationId, LocalDateTime timestamp, WeatherType weatherType,
                   Double temperature, Double windSpeed) {
    }

    static List<Reading> parse(String body) {
        List<Reading> result = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        for (String line : body.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] t = trimmed.split("\\s+");
            if (t.length < MIN_TOKENS || !t[0].matches("\\d{12}")) {
                continue;
            }
            try {
                result.add(new Reading(
                        Long.parseLong(t[1]),
                        LocalDateTime.parse(t[0], TM),
                        WeatherType.from(t[24]),  // WW 국내식 일기코드
                        temperatureOf(t[11]),     // TA 기온
                        nonNegativeOf(t[3])       // WS 풍속
                ));
            } catch (RuntimeException e) {
                // 개별 라인 파싱 실패는 건너뛴다(나머지 라인 처리를 막지 않음).
            }
        }
        return result;
    }

    private static Double temperatureOf(String raw) {
        Double v = toDouble(raw);
        return (v == null || v <= -50.0) ? null : v;
    }

    private static Double nonNegativeOf(String raw) {
        Double v = toDouble(raw);
        return (v == null || v < 0.0) ? null : v;
    }

    private static Double toDouble(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
