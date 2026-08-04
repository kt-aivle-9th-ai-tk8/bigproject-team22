package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.UpdateUserCommand;

import java.util.List;

/**
 * 사용자 통합 수정 요청(부분 수정).
 * <p>
 * record 가 아닌 setter 기반 클래스인 이유: Jackson 은 <b>본문에 키가 있을 때만</b> setter 를 호출하므로
 * "키 없음(미수정)"과 "명시적 null(초기화)"을 구분할 수 있다. record 로는 둘 다 null 로 들어와 구분이 불가능하다.
 * <p>
 * 요청의 {@code wind_farm_ids} 는 숫자 배열(Long[])이며, 응답의 id 는 문자열로 내려간다(계약 그대로).
 */
public class UpdateUserRequest {

    private Role role;
    private boolean roleProvided;

    private List<Long> windFarmIds;
    private boolean windFarmIdsProvided;

    public void setRole(Role role) {
        this.role = role;
        this.roleProvided = true;
    }

    public void setWindFarmIds(List<Long> windFarmIds) {
        this.windFarmIds = windFarmIds;
        this.windFarmIdsProvided = true;
    }

    public Role getRole() {
        return role;
    }

    public List<Long> getWindFarmIds() {
        return windFarmIds;
    }

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(roleProvided, role, windFarmIdsProvided, windFarmIds);
    }
}
