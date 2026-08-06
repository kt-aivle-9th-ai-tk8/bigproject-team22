package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application.port.UserAssignmentPort.AssignedWindFarm;

/**
 * 담당 단지 1건. id 는 JS Number 정밀도 손실을 피하기 위해 문자열로 직렬화한다.
 */
public record AssignmentResponse(
        String windFarmId,
        String windFarmName
) {
    public static AssignmentResponse from(AssignedWindFarm assignment) {
        return new AssignmentResponse(
                assignment.windFarmId() == null ? null : String.valueOf(assignment.windFarmId()),
                assignment.windFarmName());
    }
}
