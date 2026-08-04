package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.UpdateUserCommand;

import java.util.List;

/**
 * 사용자 통합 수정 요청(부분 수정).
 * <p>
 * record 가 아닌 setter 기반 클래스인 이유: Jackson 은 <b>본문에 키가 있을 때만</b> setter 를 호출하므로
 * "키 없음(미수정)"과 "명시적 null(초기화)"을 구분할 수 있다. record 로는 둘 다 null 로 들어와 구분이 불가능하다.
 * <p>
 * {@code wind_farm_ids} 는 <b>문자열 배열</b>로 받는다. 요청/응답의 모든 식별자를 문자열로 통일하는 계약에 따른 것이며
 * (JS Number 2^53 정밀도 손실 방지), 도메인 id(Long) 변환은 경계 유틸 {@link ApiIds} 가 담당한다.
 * 숫자가 아닌 값이 섞이면 400 이다.
 */
public class UpdateUserRequest {

    private Role role;
    private boolean roleProvided;

    private List<String> windFarmIds;
    private boolean windFarmIdsProvided;

    public void setRole(Role role) {
        this.role = role;
        this.roleProvided = true;
    }

    public void setWindFarmIds(List<String> windFarmIds) {
        this.windFarmIds = windFarmIds;
        this.windFarmIdsProvided = true;
    }

    public Role getRole() {
        return role;
    }

    public List<String> getWindFarmIds() {
        return windFarmIds;
    }

    public UpdateUserCommand toCommand() {
        List<Long> ids = windFarmIds == null ? null : windFarmIds.stream()
                .map(ApiIds::toLong)
                .toList();
        return new UpdateUserCommand(roleProvided, role, windFarmIdsProvided, ids);
    }
}
