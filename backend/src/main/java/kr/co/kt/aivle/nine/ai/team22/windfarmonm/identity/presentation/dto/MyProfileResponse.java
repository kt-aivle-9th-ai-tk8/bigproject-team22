package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.MyProfileResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security.PiiMasker;

/**
 * 마이페이지 응답. id 는 JS Number 정밀도 손실을 피하려고 문자열로 직렬화한다(전역 계약).
 * <p>
 * <b>자기 정보라도 사번·이름·전화번호는 마스킹한다</b> — 관리자 화면과 같은 규칙이다. 본인은 이미 아는
 * 값이지만, 가려야 하는 이유는 열람 주체가 아니라 <b>노출 경로</b>에 있다. 화면 캡처·어깨너머·공유된
 * 브라우저 세션으로 새는 것은 남의 정보든 내 정보든 똑같이 개인정보 유출이고, 마스킹은 서버에서만
 * 실질적 통제가 된다({@link PiiMasker} 참고).
 * <p>
 * <b>수정 화면을 이 응답으로 채우면 안 된다.</b> 마스킹된 값을 그대로 저장하면 원본이 별표로 덮인다 —
 * 수정은 사용자가 새로 입력한 값만 보내는 별도 API 로 다뤄야 한다.
 * <p>
 * 이메일은 담지 않는다. 스키마에 이메일 컬럼이 없다(가입 시 수집하지 않는다).
 */
public record MyProfileResponse(
        String userId,
        String employeeId,
        String userName,
        String phone,
        String department,
        Role role
) {
    public static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(
                result.id() == null ? null : String.valueOf(result.id()),
                PiiMasker.mask(result.employeeId()),
                PiiMasker.mask(result.userName()),
                PiiMasker.mask(result.phone()),
                result.department(),
                result.role());
    }
}
