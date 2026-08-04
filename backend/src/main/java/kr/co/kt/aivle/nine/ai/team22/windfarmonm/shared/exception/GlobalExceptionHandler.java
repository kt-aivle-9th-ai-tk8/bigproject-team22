package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리. 모든 실패 응답이 {@link ApiErrorResponse}(success/code/message/data) 한 가지 스키마로 나가도록
 * 프레임워크 예외까지 포괄한다(핸들러가 없으면 Spring 기본 /error 포맷이 섞여 FE 가 두 스키마를 다뤄야 한다).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, e.getMessage()));
    }

    /** @Valid 본문 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(message);
    }

    /** 쿼리/경로 파라미터 타입 불일치(예: ?top-n=all), 필수 파라미터 누락, 잘못된 JSON 본문 */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e) {
        return badRequest(ErrorCode.INVALID_INPUT.getMessage());
    }

    /** 최후 폴백. 원인은 로그로만 남기고 클라이언트에는 내부 정보를 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(false, "C999", "서버 내부 오류가 발생했습니다.", null));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }
}
