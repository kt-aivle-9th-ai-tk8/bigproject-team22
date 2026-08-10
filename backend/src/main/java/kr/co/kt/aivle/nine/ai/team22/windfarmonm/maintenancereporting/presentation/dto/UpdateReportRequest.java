package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 보고서 본문 직접 수정 요청.
 * <p>
 * 부분 수정 대상 필드가 하나뿐이라 "키 없음 vs 명시적 null" 을 구분할 필요가 없다 — 본문은 필수다.
 * (빈 문자열은 허용한다. 사용자가 내용을 통째로 지우는 것은 유효한 편집이다.)
 */
public record UpdateReportRequest(
        // 상한을 여기서 막는 이유: 넘치면 컬럼 제약 위반이 DataIntegrityViolationException 으로 올라오는데,
        // 이는 전역 핸들러의 catch-all 에 걸려 500 이 된다. 입력 오류는 400 으로 돌려줘야 한다.
        @NotNull(message = "본문은 필수입니다.")
        @Size(max = MAX_CONTEXT_LENGTH, message = "본문이 너무 깁니다.")
        String context
) {
    /** 컬럼(MEDIUMTEXT)보다 넉넉히 작게 잡아 DB 제약에 닿기 전에 거른다. */
    public static final int MAX_CONTEXT_LENGTH = 1_000_000;
}
