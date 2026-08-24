package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WeatherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KMA 지상관측 시간자료(kma_sfctm2.php) 파서 단위 테스트.
 * 실제 응답 행을 사용해 토큰 인덱스(WS[3]/TA[11]/WW[24])와 헤더 스킵/결측 정규화를 고정한다.
 */
class KmaAsosResponseParserTest {

    // 실제 응답 행(WW="-"): TM STN WD WS ... TA ... (46토큰)
    private static final String ROW_90 =
            "202608031500  90   2  2.1  -9 -9.0   -9 1010.1 1012.1  7  -0.2  28.4  25.6  85.0  32.8   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0 -9 -9 -                        6   6    4 -         -9  -9  -9  3300  0.0  1.20 -9  36.6 -99.0 -99.0 -99.0 -99.0  -9 -9.0 -9  3 -9";
    private static final String ROW_100 =
            "202608031500 100   7  3.5  -9 -9.0   -9  927.1 1010.7  3   0.5  27.3  23.9  82.0  29.7   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0 -9 -9 -                        5   1    2 -         -9  -9  -9  2539  1.0  3.25 -9  50.0 -99.0 -99.0 -99.0 -99.0  -9 -9.0 -9  3 -9";

    @Test
    @DisplayName("실제 응답 행을 토큰 인덱스로 매핑한다(STN/TM/WS/TA, WW '-' → CLEAR)")
    void parse_mapsRealRow() {
        List<KmaAsosResponseParser.Reading> result = KmaAsosResponseParser.parse(ROW_90);

        assertThat(result).hasSize(1);
        KmaAsosResponseParser.Reading r = result.get(0);
        assertThat(r.stationId()).isEqualTo(90L);
        assertThat(r.timestamp()).isEqualTo(LocalDateTime.of(2026, 8, 3, 15, 0));
        assertThat(r.windSpeed()).isEqualTo(2.1);       // WS[3]
        assertThat(r.temperature()).isEqualTo(28.4);    // TA[11]
        assertThat(r.weatherType()).isEqualTo(WeatherType.CLEAR); // WW[24] = "-"
    }

    @Test
    @DisplayName("헤더(#)/START·END 마커/빈 라인은 건너뛰고 데이터 행만 파싱한다")
    void parse_skipsHeaderLines() {
        String body = String.join("\n",
                "#START7777",
                "#  기상청 지상관측 시간자료 ...",
                "# YYMMDDHHMI STN  WD   WS ...",
                "",
                ROW_90,
                ROW_100,
                "#7777END");

        assertThat(KmaAsosResponseParser.parse(body)).hasSize(2);
    }

    @Test
    @DisplayName("결측 풍속(-9.0)/기온(-99.0)은 null 로 정규화한다")
    void parse_normalizesMissingToNull() {
        // ROW_90 에서 WS(2.1)→-9.0, TA(28.4)→-99.0 으로만 치환한 행
        String missing =
                "202608031500 901   2 -9.0  -9 -9.0   -9 1010.1 1012.1  7  -0.2 -99.0  25.6  85.0  32.8   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0 -9 -9 -                        6   6    4 -         -9  -9  -9  3300  0.0  1.20 -9  36.6 -99.0 -99.0 -99.0 -99.0  -9 -9.0 -9  3 -9";

        KmaAsosResponseParser.Reading r = KmaAsosResponseParser.parse(missing).get(0);
        assertThat(r.windSpeed()).isNull();
        assertThat(r.temperature()).isNull();
        assertThat(r.weatherType()).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    @DisplayName("운영 응답 전문을 그대로 파싱한다(2026-08-18 15:00 KST, 지점 156, tm 생략 = 최신)")
    void parse_liveResponseWithFullHeader() {
        // API허브 실응답 그대로. 헤더 4줄(#START, 자릿수 눈금, 항목명, 단위)과 데이터 1행.
        // tm 을 보내지 않으면 최신 관측이 온다(운영 확인) — 어댑터가 tm 을 다루지 않는 근거다.
        String body = String.join("\n",
                "#START7777",
                "#2345678901234567890123456789012345678901234567890123456789012345678901234567890",
                "# YYMMDDHHMI STN  WD   WS GST  GST  GST     PA     PS PT    PR    TA    TD    HM"
                        + "    PV     RN     RN     RN     RN     SD     SD     SD WC WP WW"
                        + "                      CA  CA   CH CT        CT  CT  CT    VS   SS    SI ST"
                        + "    TS    TE    TE    TE    TE  ST   WH BF IR IX",
                "#        KST  ID  16  m/s  WD   WS   TM    hPa    hPa  -   hPa     C     C     %"
                        + "   hPa     mm    DAY    JUN    INT    HR3    DAY    TOT -- -- ----------------------"
                        + " TOT MID  MIN -------- TOP MID LOW                  GD     C     5    10    20    30 SEA    m --  -  -",
                "202608181500 156  25  1.3  -9 -9.0   -9 1001.7 1009.5  7  -0.9  33.7  23.3  55.0  28.6"
                        + "   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0 -9 -9 -"
                        + "                        0   0   -9 -         -9  -9  -9  4134  0.5  1.88 -9  45.4"
                        + " -99.0 -99.0 -99.0 -99.0  -9 -9.0 -9  3  2");

        List<KmaAsosResponseParser.Reading> result = KmaAsosResponseParser.parse(body);

        assertThat(result).hasSize(1);
        KmaAsosResponseParser.Reading r = result.get(0);
        assertThat(r.stationId()).isEqualTo(156L);
        assertThat(r.timestamp()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));
        assertThat(r.windSpeed()).isEqualTo(1.3);
        assertThat(r.temperature()).isEqualTo(33.7);
        assertThat(r.weatherType()).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    @DisplayName("null/공백 응답은 빈 목록을 반환한다")
    void parse_emptyBodyReturnsEmpty() {
        assertThat(KmaAsosResponseParser.parse(null)).isEmpty();
        assertThat(KmaAsosResponseParser.parse("   ")).isEmpty();
    }
}
