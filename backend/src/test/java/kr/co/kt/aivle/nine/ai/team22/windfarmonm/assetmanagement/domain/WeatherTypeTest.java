package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WW(국내식 일기코드) → 날씨유형 우선순위 매핑 단위 테스트.
 */
class WeatherTypeTest {

    @Test
    @DisplayName("단일 코드는 해당 유형으로 매핑된다")
    void from_singleCode() {
        assertThat(WeatherType.from("01")).isEqualTo(WeatherType.RAIN);
        assertThat(WeatherType.from("22")).isEqualTo(WeatherType.BLIZZARD);
        assertThat(WeatherType.from("92")).isEqualTo(WeatherType.CLOUDY);
        assertThat(WeatherType.from("90")).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    @DisplayName("여러 코드가 packed 되면 가장 높은 우선순위 유형을 선택한다")
    void from_packedCodes_picksHighestPriority() {
        // 01(RAIN, 우선순위 10) + 70(THUNDERSTORM, 3) → THUNDERSTORM
        assertThat(WeatherType.from("0170")).isEqualTo(WeatherType.THUNDERSTORM);
        // 22(BLIZZARD, 2) + 90(CLEAR, 13) → BLIZZARD
        assertThat(WeatherType.from("2290")).isEqualTo(WeatherType.BLIZZARD);
        // 85(STORM, 1) 가 항상 최우선
        assertThat(WeatherType.from("018522")).isEqualTo(WeatherType.STORM);
    }

    @Test
    @DisplayName("빈 값('-')/null/공백은 CLEAR 로 본다")
    void from_emptyIsClear() {
        assertThat(WeatherType.from("-")).isEqualTo(WeatherType.CLEAR);
        assertThat(WeatherType.from("")).isEqualTo(WeatherType.CLEAR);
        assertThat(WeatherType.from(null)).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    @DisplayName("매핑되지 않는 코드(예: 75 지진)만 있으면 CLEAR 로 본다")
    void from_unmappedIsClear() {
        assertThat(WeatherType.from("75")).isEqualTo(WeatherType.CLEAR);
    }
}
