package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response;

/**
 * 공통 응답 포맷. 성공/실패 여부와 메시지, 데이터를 감싼다.
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
