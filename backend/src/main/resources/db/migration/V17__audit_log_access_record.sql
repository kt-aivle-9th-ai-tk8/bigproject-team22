-- 감사 로그를 '개인정보 접속기록'으로 쓸 수 있게 보강한다.
--
-- 「개인정보의 안전성 확보조치 기준」(개인정보보호위원회 고시) 제8조는 접속기록에 계정·접속일시·
-- 접속지 정보·처리한 정보주체 정보·수행업무를 남기도록 요구한다. V6 의 audit_log 에는 이 중
-- **접속지 정보**가 없어 그대로는 요건을 채우지 못했다.
--
-- 참고) 보존기간은 최소 1년, 5만명 이상 정보주체의 개인정보 또는 고유식별정보·민감정보를 처리하는
--      경우 2년이다. 위·변조 방지를 위해 애플리케이션은 이 테이블에 INSERT 만 수행한다
--      (운영 DB 계정에서 UPDATE/DELETE 권한을 회수하는 것이 정석이며, 그 조치는 인프라 소관).

ALTER TABLE audit_log
    ADD COLUMN ip_address VARCHAR(45) DEFAULT NULL
        COMMENT '접속지 정보. IPv6(최대 45자)까지 수용. 요청 밖(배치 등) 발생분은 NULL';

-- V6 주석의 예시 목록(report_approve 등)은 승인 기작 폐기로 더 이상 유효하지 않다. 실제 사용 값으로 갱신한다.
ALTER TABLE audit_log
    MODIFY COLUMN action_type VARCHAR(50) NOT NULL
        COMMENT 'LOGIN / LOGIN_FAILED / LOGOUT / USER_LIST_VIEW / USER_ROLE_CHANGE / USER_STATUS_CHANGE / USER_REJECT / USER_FORCE_LOGOUT / REPORT_CREATE / REPORT_UPDATE / REPORT_DELETE';

-- 보존기간 관리와 정기 점검(월 1회 이상)은 사용자 구분 없는 기간 스캔이라
-- 기존 (user_id, created_at) 인덱스가 듣지 않는다.
CREATE INDEX idx_audit_log_created ON audit_log (created_at);

-- 행위 주체 FK 를 뗀다.
--
-- 접속기록은 계정이 사라져도 보존되어야 한다(법정 보존기간이 계정 수명보다 길다). FK 를 두면 반대가 된다 —
-- 기록이 계정 삭제를 막는다. 실제로 가입 신청 후 로그인을 시도한 GUEST 는 LOGIN_FAILED 기록이 남아
-- '가입 거절'(계정 삭제)이 U004(참조 중)로 거부되어 버린다. 거절해야 할 계정을 거절하지 못하는 것도,
-- 거절하려고 기록을 지우는 것도 모두 틀렸다.
--
-- 그래서 user_id 는 target_table/target_id 와 같은 성격의 '값 참조'가 된다(V6 주석의 그 이유 그대로).
-- 조회 시 조인은 여전히 되며, 계정이 지워진 뒤에는 주체 정보가 id 로만 남는다.
ALTER TABLE audit_log
    DROP FOREIGN KEY fk_audit_log_user;
