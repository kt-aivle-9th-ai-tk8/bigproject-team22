package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리. 모든 실패 응답이 {@link ApiErrorResponse}(success/code/message/data) 한 가지 스키마로 나가도록
 * 프레임워크 예외까지 포괄한다.
 * <p>
 * {@link ResponseEntityExceptionHandler} 를 상속하는 이유: Spring MVC 표준 예외(405/415/406/404/400 …)는
 * 상위 클래스가 <b>예외별로 올바른 상태코드</b>를 이미 정해 둔다. 이를 상속하지 않고 {@code Exception} catch-all 만
 * 두면, 표준 예외가 catch-all 에 먼저 걸려 Spring 이 상태코드를 정할 기회를 잃고 <b>전부 500</b> 이 된다
 * (예: GET 전용 경로에 POST → 405 가 아니라 500). 개별 핸들러를 나열하는 방식은 빠뜨린 예외가 다시 500 이 되므로,
 * 표준 예외 처리는 상위 클래스에 위임하고 여기서는 <b>본문 포맷만</b> 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_ERROR_CODE = "C999";
    private static final String INTERNAL_ERROR_MESSAGE = "서버 내부 오류가 발생했습니다.";

    /** 도메인 규칙 위반. 상태코드와 code 는 {@link ErrorCode} 가 정한다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, e.getMessage()));
    }

    /** 최후 폴백. 원인은 로그로만 남기고 클라이언트에는 내부 정보를 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(false, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, null));
    }

    /** @Valid 본문 검증 실패는 어떤 필드가 왜 틀렸는지 알려준다(상위 클래스 기본 메시지는 장황하다). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return super.handleExceptionInternal(ex, ApiErrorResponse.of(ErrorCode.INVALID_INPUT, message),
                headers, status, request);
    }

    /**
     * Spring MVC 표준 예외의 <b>본문만</b> 우리 포맷으로 바꾼다. 상태코드는 상위 클래스가 정한 값을 그대로 쓴다.
     * 원인 상세는 로그로 남기고 클라이언트에는 상태별 고정 메시지를 준다(프레임워크 예외 메시지에 내부 정보가
     * 섞일 수 있으므로). FE 는 이 부류를 HTTP 상태로 구분하면 된다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers, HttpStatusCode status,
                                                             WebRequest request) {
        if (body instanceof ApiErrorResponse) { // 위 오버라이드에서 이미 만든 본문은 그대로 통과
            return super.handleExceptionInternal(ex, body, headers, status, request);
        }
        if (status.is5xxServerError()) {
            log.error("처리되지 않은 서블릿 예외(status={})", status, ex);
        } else {
            log.warn("잘못된 요청(status={}): {}", status, ex.getMessage());
        }
        ApiErrorResponse errorBody = new ApiErrorResponse(false, codeOf(status), messageOf(status), null);
        return super.handleExceptionInternal(ex, errorBody, headers, status, request);
    }

    private static String codeOf(HttpStatusCode status) {
        return status.is5xxServerError() ? INTERNAL_ERROR_CODE : ErrorCode.INVALID_INPUT.getCode();
    }

    private static String messageOf(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return INTERNAL_ERROR_MESSAGE;
        }
        return switch (status.value()) {
            case 404 -> "요청한 리소스를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 메서드입니다.";
            case 406 -> "지원하지 않는 응답 형식입니다.";
            case 415 -> "지원하지 않는 미디어 타입입니다.";
            default -> ErrorCode.INVALID_INPUT.getMessage();
        };
    }
}
