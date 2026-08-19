package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain;

/**
 * 감사 로그 저장소. <b>적재 전용</b>이다 — 수정·삭제 메서드를 두지 않아 위·변조 경로를 코드에서 없앤다.
 * 조회는 아직 요구가 없어 열지 않았다(필요해지면 기간·주체 기준 조회를 여기에 추가할 것).
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);
}
