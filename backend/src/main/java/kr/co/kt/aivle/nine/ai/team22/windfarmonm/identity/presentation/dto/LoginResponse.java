package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security.PiiMasker;

/**
 * 로그인 응답. <b>본인 정보도 마스킹한다</b> — 응답 DTO 에 평문 개인정보를 싣지 않는다는 원칙에 예외를 두지 않는다.
 * <p>
 * "본인 것이니 가릴 필요 없다"는 전제는 화면 앞의 사람이 계정 주인일 때만 성립한다. FE 가 이 응답을
 * {@code localStorage.userInfo} 로 오래 보관하므로, 세션·단말이 탈취되거나 공용 PC 가 그대로 남으면
 * 그 값은 "이 계정이 누구 것인지"를 알려주는 단서가 된다. 사번은 로그인 ID 이기도 해서 노출 시
 * 대입 공격의 절반이 채워진다.
 * <p>
 * 세션 주체({@code LoginMember})는 이 DTO 가 아니라 {@code LoginResult} 원문으로 만들어지므로
 * (AuthController 참고) 마스킹이 인증·인가에 영향을 주지 않는다.
 * <p>
 * <b>FE 영향</b>: 이 값으로는 "홍길동님" 표시를 만들 수 없다. 본인 이름 표시가 필요해지면
 * 평문을 다시 흘리는 대신 본인 전용 조회 엔드포인트를 따로 두고 거기서 정책을 정하는 것이 맞다.
 */
public record LoginResponse(
        String employeeId,
        String userName,
        Role role
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                PiiMasker.mask(result.employeeId()),
                PiiMasker.mask(result.userName()),
                result.role());
    }
}
