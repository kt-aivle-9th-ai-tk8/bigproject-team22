package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

/**
 * 최신 발전량 요약. current(현재출력 kW), today(당일 누적 kWh), month(당월 누적 kWh).
 */
public record PowerSummary(
        Double currentPower,
        Double todayPower,
        Double monthPower
) {
    public static PowerSummary empty() {
        return new PowerSummary(null, null, null);
    }
}
