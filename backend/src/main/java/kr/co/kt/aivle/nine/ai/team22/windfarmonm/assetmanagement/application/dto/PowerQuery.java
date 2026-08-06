package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 발전량 시계열 조회 조건. 원시 문자열 입력을 검증/파싱하고 term 절삭 규칙을 적용한다.
 */
public record PowerQuery(
        LocalDateTime start,
        LocalDateTime end,
        PowerTerm term
) {
    /**
     * 원시 쿼리 파라미터를 검증/파싱한다.
     * - 시간 누락/ISO8601 미준수 → 400
     * - 알 수 없는 term → 400
     * - 기간 역전(start > end) → 400
     * <p>
     * TODO: 조회 기간 상한(term 별 최대 버킷 수/최대 기간) 검증 추가 — 상한값은 FE 와 협의 후 확정한다.
     *  현재는 상한이 없어 매우 긴 기간이 그대로 조회된다.
     * start/end 는 원시값 그대로 보존한다(절삭하지 않음). 이렇게 해야 조회 상한이 실제 end 가 되어
     * HOURLY 마지막 버킷이 end 직전까지의 데이터를 포함한다. 버킷 그리드 정렬은
     * 집계 단계(PowerQueryService.aggregate)의 term.truncate 가 담당한다.
     */
    public static PowerQuery of(String startTime, String endTime, String term) {
        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        PowerTerm parsedTerm = parseTerm(term);
        LocalDateTime start = parse(startTime);
        LocalDateTime end = parse(endTime);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        return new PowerQuery(start, end, parsedTerm);
    }

    private static PowerTerm parseTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return PowerTerm.valueOf(term.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static LocalDateTime parse(String iso8601) {
        try {
            return LocalDateTime.parse(iso8601);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
