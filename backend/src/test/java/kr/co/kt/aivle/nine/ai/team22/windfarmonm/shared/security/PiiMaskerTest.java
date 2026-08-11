package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스킹 규칙(3등분·가운데 마스킹·마스킹 길이 올림)을 길이별로 고정한다.
 * 규칙이 바뀌면 FE 표시 폭과 검색 동작이 함께 흔들리므로 표를 그대로 박아 둔다.
 */
class PiiMaskerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("길이별 마스킹 결과를 고정한다")
    @CsvSource({
            "김,                *",
            "이든,              이*",
            "홍길동,            홍*동",
            "남궁민수,          남**수",
            "abcde,             ab**e",
            "abcdef,            ab**ef",
            "2401001,           24***01",
            "24010012,          240***12",
            "01012345678,       0101****678",
    })
    void masksMiddleThird(String raw, String expected) {
        assertThat(PiiMasker.mask(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("2글자도 예외 없이 마스킹된다 — 올림 때문에 마스킹 길이가 0이 되지 않는다")
    void twoCharacters_areStillMasked() {
        assertThat(PiiMasker.mask("이든")).isEqualTo("이*").contains("*");
    }

    @Test
    @DisplayName("1글자도 마스킹된다")
    void singleCharacter_isFullyMasked() {
        assertThat(PiiMasker.mask("김")).isEqualTo("*");
    }

    @ParameterizedTest
    @DisplayName("어떤 값이든 원문이 그대로 남지 않는다")
    @ValueSource(strings = {"김", "이든", "홍길동", "남궁민수", "2401001", "01012345678"})
    void neverReturnsRawValue(String raw) {
        assertThat(PiiMasker.mask(raw)).isNotEqualTo(raw);
    }

    @Test
    @DisplayName("마스킹해도 길이는 보존된다")
    void preservesLength() {
        assertThat(PiiMasker.mask("2401001")).hasSameSizeAs("2401001");
        assertThat(PiiMasker.mask("홍길동")).hasSameSizeAs("홍길동");
    }

    @Test
    @DisplayName("null 과 빈 문자열은 그대로 통과한다")
    void nullAndEmpty_passThrough() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEmpty();
    }
}
