package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;

import java.util.List;

/**
 * 사용자 통합 수정 명령(부분 수정).
 * <p>
 * {@code *Provided} 는 <b>요청 본문에 키가 존재했는지</b>를 뜻한다(값이 null 이어도 true).
 * 키가 없으면 해당 항목은 변경하지 않고, 키가 있으면 값(빈 배열 포함)으로 교체한다.
 *
 * @param role           키가 있는데 값이 null 이면 계약 위반(role 은 NN)이라 INVALID_INPUT 으로 거절한다
 * @param windFarmIds    null 또는 빈 배열이면 담당 배정 전체 해제
 */
public record UpdateUserCommand(
        boolean roleProvided,
        Role role,
        boolean windFarmIdsProvided,
        List<Long> windFarmIds
) {
}
