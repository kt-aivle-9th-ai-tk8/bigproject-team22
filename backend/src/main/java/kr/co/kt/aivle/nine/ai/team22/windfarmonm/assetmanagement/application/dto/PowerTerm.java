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

        @Override
        public LocalDateTime next(LocalDateTime bucket) {
            return bucket.plusHours(1);
        }
    },
    DAILY {
        @Override
        public LocalDateTime truncate(LocalDateTime time) {
            return time.truncatedTo(ChronoUnit.DAYS);
        }

        @Override
        public LocalDateTime next(LocalDateTime bucket) {
            return bucket.plusDays(1);
        }
    },
    MONTHLY {
        @Override
        public LocalDateTime truncate(LocalDateTime time) {
            return time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        }

        @Override
        public LocalDateTime next(LocalDateTime bucket) {
            return bucket.plusMonths(1);
        }
    };

    /** 주어진 시각을 이 단위의 버킷 시작으로 절삭한다. */
    public abstract LocalDateTime truncate(LocalDateTime time);

    /** 이 단위로 다음 버킷 시작 시각을 반환한다(그리드 생성용). */
    public abstract LocalDateTime next(LocalDateTime bucket);
}
