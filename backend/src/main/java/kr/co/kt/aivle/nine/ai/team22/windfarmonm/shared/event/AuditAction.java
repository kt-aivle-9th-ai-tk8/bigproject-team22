package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event;

/**
 * 감사 로그의 수행업무 구분({@code audit_log.action_type}).
 * <p>
 * 앞의 여덟은 <b>개인정보 접속기록</b>(고시 제8조)이고, 뒤의 셋은 보고서 거버넌스 이력이다.
 * 법정 기록과 업무 이력을 한 테이블에 담되 이 구분으로 나눠 볼 수 있게 한다
 * ({@link #isAccessRecord()} — 보존기간 산정과 정기 점검의 대상 판별에 쓴다).
 * <p>
 * 이름을 바꾸면 이미 적재된 과거 기록과 대조가 끊긴다. 값은 추가만 하고 개명하지 말 것.
 */
public enum AuditAction {

    LOGIN(true),
    /** 비밀번호 불일치·잠긴 계정·미승인 계정. 사번이 실재하지 않으면 주체를 특정할 수 없어 남기지 못한다. */
    LOGIN_FAILED(true),
    LOGOUT(true),
    /** 관리자의 회원목록 조회 — 개인정보 '조회'도 접속기록 대상이다. */
    USER_LIST_VIEW(true),
    USER_ROLE_CHANGE(true),
    USER_STATUS_CHANGE(true),
    /** 가입 거절(계정 삭제). */
    USER_REJECT(true),
    /** 관리자에 의한 세션 강제 종료. */
    USER_FORCE_LOGOUT(true),

    REPORT_CREATE(false),
    REPORT_UPDATE(false),
    REPORT_DELETE(false);

    private final boolean accessRecord;

    AuditAction(boolean accessRecord) {
        this.accessRecord = accessRecord;
    }

    /** 개인정보처리시스템 접속기록(법정 보존 대상)인가. */
    public boolean isAccessRecord() {
        return accessRecord;
    }
}
