package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event;

/**
 * 감사 대상 행위가 일어났음을 알리는 이벤트. 발행 측(각 BC 의 application)은 <b>무슨 일이 있었는지</b>만
 * 담고, 접속지(IP)와 행위 주체 해석은 수신 측이 요청 컨텍스트에서 채운다 — 그래야 모든 발행 지점에
 * HttpServletRequest 를 끌고 다니지 않는다.
 * <p>
 * 수신자는 같은 스레드·같은 트랜잭션에서 동기 처리한다. 업무 변경과 감사 기록이 함께 커밋되거나 함께
 * 사라지므로 "변경은 됐는데 기록이 없는" 구멍이 생기지 않는다 — 접속기록에서는 누락이 곧 결함이다.
 *
 * @param action       수행업무
 * @param targetTable  대상 테이블(예: {@code user}, {@code report}). 대상이 없으면 null
 * @param targetId     대상 행 식별자. 대상이 없거나 단건이 아니면 null
 * @param actorUserId  행위 주체를 명시할 때만 지정한다. null 이면 수신 측이 <b>현재 세션</b>에서 찾는다.
 *                     로그인·로그인 실패는 아직 세션이 없으므로 반드시 명시해야 한다
 */
public record AuditEvent(AuditAction action, String targetTable, Long targetId, Long actorUserId) {

    /** 현재 로그인 사용자가 주체인 행위. */
    public static AuditEvent of(AuditAction action, String targetTable, Long targetId) {
        return new AuditEvent(action, targetTable, targetId, null);
    }

    /** 주체가 세션에서 확인되지 않는 행위(로그인·로그인 실패). */
    public static AuditEvent by(Long actorUserId, AuditAction action, String targetTable, Long targetId) {
        return new AuditEvent(action, targetTable, targetId, actorUserId);
    }
}
