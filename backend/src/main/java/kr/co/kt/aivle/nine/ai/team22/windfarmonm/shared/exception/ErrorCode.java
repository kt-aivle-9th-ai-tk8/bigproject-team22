package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A002", "사번 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "A003", "로그인 실패 횟수 초과로 잠긴 계정입니다. 관리자에게 문의하세요."),
    ACCOUNT_PENDING(HttpStatus.FORBIDDEN, "A004", "회원가입 승인 대기 중입니다. 관리자 승인 후 이용할 수 있습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),

    // User
    DUPLICATE_EMPLOYEE_ID(HttpStatus.CONFLICT, "U001", "이미 존재하는 사번입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "사용자를 찾을 수 없습니다.");
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "사용자를 찾을 수 없습니다."),

    // Monitoring - WindFarm / Turbine / Blade
    WIND_FARM_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "풍력단지를 찾을 수 없습니다."),
    WIND_FARM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "M002", "해당 풍력단지에 대한 접근 권한이 없습니다."),
    TURBINE_NOT_FOUND(HttpStatus.NOT_FOUND, "M003", "터빈을 찾을 수 없습니다."),
    TURBINE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "M004", "해당 터빈에 대한 접근 권한이 없습니다."),
    BLADE_NOT_FOUND(HttpStatus.NOT_FOUND, "M005", "블레이드를 찾을 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "M006", "조회 기간이 올바르지 않습니다."),

    private final HttpStatus status;
    private final String code;
    private final String message;
}
