package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;

/**
 * 외부(경로변수/요청 바디)에서 문자열로 들어온 식별자를 내부 도메인 id(Long)로 변환하는 경계 유틸.
 * <p>
 * API 계약상 모든 식별자는 문자열이며(JS Number 2^53 정밀도 손실 방지), 컨트롤러는 String 을 받고
 * 이 헬퍼로 도메인 타입으로 변환한다. 형식 오류는 400({@link ErrorCode#INVALID_INPUT}).
 * 내부 id 표현이 향후 바뀌어도(예: UUIDv7) 변환 지점이 이 한 곳으로 국한된다.
 */
// @UtilityClass // experimental 패키지로써, 이 어노테이션의 역할을 직접 아래에서 설정. 안내측면에서 주석으로 어노테이션 유지
public final class ApiIds {

    private ApiIds() {
    }

    /** 문자열 식별자를 Long 으로 변환. 숫자가 아니면 400. */
    public static long toLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
