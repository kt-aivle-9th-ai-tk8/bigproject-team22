package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 발전량 시계열 집계 단위. 각 단위의 하위 시간값을 0으로 절삭하는 규칙을 함께 정의한다.
 * (HOURLY: 분↓=0, DAILY: 시↓=0, MONTHLY: 일↓=1일 0시)
 */
public enum PowerTerm {
    HOURLY {
        @Override
        public LocalDateTime truncate(LocalDateTime time) {
            return time.truncatedTo(ChronoUnit.HOURS);
        }
    },
    DAILY {
        @Override
        public LocalDateTime truncate(LocalDateTime time) {
            return time.truncatedTo(ChronoUnit.DAYS);
        }
    },
    MONTHLY {
        @Override
        public LocalDateTime truncate(LocalDateTime time) {
            return time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        }
    };

    /**
     * 주어진 시각을 이 단위의 버킷 시작으로 절삭한다.
     * 집계행이 버킷 시작 시각에 저장되므로 <b>조회 하한</b>과 <b>버킷 키</b> 계산에 사용한다
     * (조회 상한은 절삭하지 않는다 — 마지막 버킷 구간의 데이터가 누락되지 않도록).
     */
    public abstract LocalDateTime truncate(LocalDateTime time);
}
