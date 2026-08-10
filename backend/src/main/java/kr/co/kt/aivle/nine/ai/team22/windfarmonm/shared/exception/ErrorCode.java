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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "사용자를 찾을 수 없습니다."),

    // Monitoring - WindFarm / Turbine / Blade
    // 담당이 아닌 자원은 "권한 없음"이 아니라 "없음"으로 응답한다(AssetAccessGuard 참고).
    // 그래서 자원별 ACCESS_DENIED 코드(구 M002/M004/M007)는 두지 않는다 — 쓰이면 존재가 드러난다.
    WIND_FARM_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "풍력단지를 찾을 수 없습니다."),
    TURBINE_NOT_FOUND(HttpStatus.NOT_FOUND, "M003", "터빈을 찾을 수 없습니다."),
    BLADE_NOT_FOUND(HttpStatus.NOT_FOUND, "M005", "블레이드를 찾을 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "M006", "조회 기간이 올바르지 않습니다."),

    // Report
    // 열람 권한이 없는 보고서도 REPORT_NOT_FOUND 로 응답한다. 403/404 를 구분하면 담당이 아닌 사용자에게
    // 보고서의 존재 사실이 드러나기 때문이다(다른 관제 자원과 달리 별도 ACCESS_DENIED 코드를 두지 않는다).
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "보고서를 찾을 수 없습니다."),
    INVALID_REPORT_TYPE(HttpStatus.BAD_REQUEST, "R002", "지원하지 않는 보고서 유형입니다."),
    INVALID_REPORT_PERIOD(HttpStatus.BAD_REQUEST, "R003", "보고서 기간이 올바르지 않습니다."),
    // 대상 발전소/터빈 지정이 실재하지 않거나 서로 맞지 않을 때. 복합 FK 위반(교차 컬럼 정합성)을
    // 사용자 오류로 번역한 것이라, 어느 쪽이 문제인지는 구분하지 않는다(포괄 400).
    INVALID_REPORT_TARGET(HttpStatus.BAD_REQUEST, "R004", "보고서 대상(발전소/터빈) 지정이 올바르지 않습니다."),

    // Notification — 타인 소유 알림도 동일하게 NOT_FOUND(존재 은닉)
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),

    // Inspection / Defect
    INSPECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "점검 요청을 찾을 수 없습니다."),
    INSPECTION_STATE_CONFLICT(HttpStatus.CONFLICT, "D002", "현재 상태에서 수행할 수 없는 작업입니다."),
    INVALID_INSPECTION_TARGET(HttpStatus.BAD_REQUEST, "D003", "요청한 터빈/블레이드가 해당 단지에 속하지 않습니다."),
    // 422: RFC 9110 에서 명칭이 UNPROCESSABLE_CONTENT 로 바뀌었다(구 UNPROCESSABLE_ENTITY 는 deprecated).
    VISION_RESULT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "D004", "결함 분석 결과를 해석할 수 없습니다."),
    WEBHOOK_UNAUTHORIZED(HttpStatus.FORBIDDEN, "D005", "웹훅 인증에 실패했습니다."),

    // Infrastructure — 외부 자원 미설정/장애. 앱 기동은 막지 않고 해당 엔드포인트만 실패시킨다.
    STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "S001", "파일 저장소가 설정되지 않았습니다."),
    STORAGE_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "S002", "파일 저장소 접근에 실패했습니다."),
    INFERENCE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "S003", "추론 엔드포인트가 설정되지 않았습니다."),
    INFERENCE_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "S004", "추론 엔드포인트 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
