package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.port;

import java.util.List;

/**
 * 알림 수신자 조회 포트. 소비자(monitoringdiagnosis)가 소유한다.
 * 이상 보고서 발생 시 수신자는 <b>ADMIN 전원 + 해당 단지 담당자</b>다.
 */
public interface NotificationRecipientPort {

    /** ADMIN 역할 사용자 id 목록. */
    List<Long> adminUserIds();

    /** 해당 단지를 담당하는 사용자 id 목록. */
    List<Long> assignedUserIds(Long windFarmId);
}
