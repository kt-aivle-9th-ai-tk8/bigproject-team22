package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 대시보드 날씨 유형(FE 합의 enum). 기상청 지상관측(ASOS)의 국내식 일기코드(WW, 2자리)를
 * 우선순위대로 매핑한다. <b>선언 순서가 곧 우선순위</b>(위일수록 높음)이다.
 * {@link #UNKNOWN} 은 조회 실패/무자료 표시 전용이며 코드 매핑 대상이 아니다.
 */
public enum WeatherType {

    STORM(Set.of("33", "48", "85")),                                                          // 태풍(용오름/회오리바람/폭풍)
    BLIZZARD(Set.of("22")),                                                                   // 눈보라
    THUNDERSTORM(Set.of("70", "71", "72", "73")),                                             // 뇌우
    FREEZING_RAIN(Set.of("03", "07", "12")),                                                  // 어는 비
    HAIL(Set.of("13", "14", "15")),                                                           // 우박
    ICING(Set.of("23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "34", "35", "36")), // 결빙
    SLEET(Set.of("06", "09")),                                                                // 진눈깨비
    SNOW(Set.of("05", "08", "10", "11", "20", "21", "80", "81")),                             // 눈
    SHOWER(Set.of("04", "86")),                                                               // 소나기
    RAIN(Set.of("01", "02")),                                                                 // 비
    FOG(Set.of("16", "17", "18", "19", "40", "41", "42", "43", "44", "45", "46", "47")),      // 안개·연무·황사·먼지
    CLOUDY(Set.of("82", "83", "92")),                                                         // 흐림
    CLEAR(Set.of("50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "74", "84", "90", "91")), // 맑음
    UNKNOWN(Set.of());                                                                        // 조회 실패/무자료 전용

    private final Set<String> codes;

    WeatherType(Set<String> codes) {
        this.codes = codes;
    }

    /**
     * WW 필드(2자리 코드 최대 11개 packed, 없으면 "-")에서 가장 높은 우선순위 유형을 도출한다.
     * 매핑되는 현상 코드가 하나도 없으면(빈 값/미매핑) {@link #CLEAR}(맑음)로 본다.
     * (조회 실패/무자료의 {@link #UNKNOWN} 은 어댑터가 별도로 설정한다.)
     */
    public static WeatherType from(String wwField) {
        Set<String> present = extractCodes(wwField);
        if (!present.isEmpty()) {
            for (WeatherType type : values()) { // 선언 순서 = 우선순위
                if (type != UNKNOWN && !Collections.disjoint(type.codes, present)) {
                    return type;
                }
            }
        }
        return CLEAR;
    }

    /** WW 토큰을 2자리 숫자 코드들로 분해한다. "-"/공백/비숫자 잔여는 무시한다. */
    private static Set<String> extractCodes(String wwField) {
        Set<String> codes = new LinkedHashSet<>();
        if (wwField == null) {
            return codes;
        }
        String s = wwField.trim();
        for (int i = 0; i + 2 <= s.length(); i += 2) {
            String pair = s.substring(i, i + 2);
            if (pair.chars().allMatch(Character::isDigit)) {
                codes.add(pair);
            }
        }
        return codes;
    }
}
