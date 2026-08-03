package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 발전량 조회 조건 파싱 단위 테스트. #2(HOURLY 마지막 버킷) 회귀 방지: start/end 를 절삭하지 않음을 고정한다.
 */
class PowerQueryTest {

    @Test
    @DisplayName("start/end 를 절삭하지 않고 원시값 그대로 보존한다")
    void of_keepsRawStartEnd() {
        PowerQuery q = PowerQuery.of("2026-08-03T10:15", "2026-08-03T14:30", "HOURLY");

        assertThat(q.start()).isEqualTo(LocalDateTime.of(2026, 8, 3, 10, 15));
        assertThat(q.end()).isEqualTo(LocalDateTime.of(2026, 8, 3, 14, 30)); // 절삭 시 14:00 이 되어 마지막 30분 누락
        assertThat(q.term()).isEqualTo(PowerTerm.HOURLY);
    }

    @Test
    @DisplayName("기간 역전(start > end)은 예외")
    void of_reversedRange_throws() {
        assertThatThrownBy(() -> PowerQuery.of("2026-08-03T15:00", "2026-08-03T14:00", "HOURLY"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("시간 누락/미지정 term 은 예외")
    void of_invalidInputs_throw() {
        assertThatThrownBy(() -> PowerQuery.of(null, "2026-08-03T14:00", "HOURLY"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PowerQuery.of("2026-08-03T10:00", "2026-08-03T14:00", "WEEKLY"))
                .isInstanceOf(BusinessException.class);
    }
}
