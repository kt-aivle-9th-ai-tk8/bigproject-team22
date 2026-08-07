package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;

/**
 * 실패 응답 포맷. 성공 응답({@link ApiResponse})과 동일한 골격에 <b>기계 판독용 code</b> 를 더한다.
 * <p>
 * FE 가 한국어 메시지 문자열 비교에 의존하지 않도록, 같은 상태코드를 쓰는 서로 다른 실패 사유
 * (예: 403 이 단지/터빈 접근 거부 등 여러 종류)를 code 로 구분한다.
 */
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        Object data
) {
    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(false, errorCode.getCode(), message, null);
    }
}
