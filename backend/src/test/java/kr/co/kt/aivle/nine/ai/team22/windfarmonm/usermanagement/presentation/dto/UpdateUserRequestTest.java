package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto.UpdateUserCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부분 수정(PATCH) 요청의 <b>"키 없음(미수정)" vs "명시적 null(초기화)"</b> 구분을 고정한다.
 * 이 구분은 Jackson 이 키가 있을 때만 setter 를 호출하는 동작에 의존하므로 회귀에 취약하다.
 * (매퍼 설정은 application.yaml 의 property-naming-strategy: SNAKE_CASE 를 반영한다.)
 */
class UpdateUserRequestTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private UpdateUserCommand parse(String json) {
        return mapper.readValue(json, UpdateUserRequest.class).toCommand();
    }

    @Test
    @DisplayName("빈 본문이면 아무것도 수정하지 않는다")
    void emptyBody_updatesNothing() {
        UpdateUserCommand command = parse("{}");

        assertThat(command.roleProvided()).isFalse();
        assertThat(command.windFarmIdsProvided()).isFalse();
    }

    @Test
    @DisplayName("role 만 보내면 담당 단지는 건드리지 않는다")
    void roleOnly_doesNotTouchAssignments() {
        UpdateUserCommand command = parse("{\"role\":\"MANAGER\"}");

        assertThat(command.roleProvided()).isTrue();
        assertThat(command.role()).isEqualTo(Role.MANAGER);
        assertThat(command.windFarmIdsProvided()).isFalse();
    }

    @Test
    @DisplayName("wind_farm_ids 만 보내면 권한은 건드리지 않는다(snake_case 바인딩 확인)")
    void windFarmIdsOnly_doesNotTouchRole() {
        UpdateUserCommand command = parse("{\"wind_farm_ids\":[1,2,3]}");

        assertThat(command.windFarmIdsProvided()).isTrue();
        assertThat(command.windFarmIds()).containsExactly(1L, 2L, 3L);
        assertThat(command.roleProvided()).isFalse();
    }

    @Test
    @DisplayName("빈 배열은 '담당 전체 해제'로 전달된다")
    void emptyArray_clearsAssignments() {
        UpdateUserCommand command = parse("{\"wind_farm_ids\":[]}");

        assertThat(command.windFarmIdsProvided()).isTrue();
        assertThat(command.windFarmIds()).isEmpty();
    }

    @Test
    @DisplayName("명시적 null 은 '키 없음'과 구분되어 초기화로 전달된다")
    void explicitNull_isDistinguishedFromAbsent() {
        UpdateUserCommand explicitNull = parse("{\"wind_farm_ids\":null}");
        assertThat(explicitNull.windFarmIdsProvided()).isTrue(); // 키가 있었음 → 초기화
        assertThat(explicitNull.windFarmIds()).isNull();

        UpdateUserCommand absent = parse("{}");
        assertThat(absent.windFarmIdsProvided()).isFalse(); // 키가 없었음 → 미수정
    }

    @Test
    @DisplayName("role 과 wind_farm_ids 를 함께 보낼 수 있다")
    void bothFields() {
        UpdateUserCommand command = parse("{\"role\":\"GUEST\",\"wind_farm_ids\":[7]}");

        assertThat(command.roleProvided()).isTrue();
        assertThat(command.role()).isEqualTo(Role.GUEST);
        assertThat(command.windFarmIdsProvided()).isTrue();
        assertThat(command.windFarmIds()).isEqualTo(List.of(7L));
    }
}
