-- 알림(notification)·작업이력(audit_log)·아웃박스(outbox_event) + users 부서 컬럼.
-- ERD 에는 있으나 아직 스키마에 없던 것들 — report-agent 와는 무관한 별개 기능이라
-- V5(monitoringdiagnosis)와 분리한다. 해당 기능 담당자가 자기 마이그레이션으로 가져가면
-- 이 파일은 통째로 버려도 된다(단, 이미 적용된 뒤라면 Flyway 이력이 남으므로 삭제 금지 —
-- 그때는 되돌리는 마이그레이션을 새로 쓸 것).
--
-- notification 이 report 를 참조하므로 V5 뒤에 와야 한다.


-- users 보강 — ERD 의 소속 부서.
-- 이 컬럼이 없던 시절의 기존 행이 있으므로 nullable 로 둔다(없는 값을 지어내지 않는다).
ALTER TABLE users
    ADD COLUMN department VARCHAR(50) DEFAULT NULL AFTER user_name;


-- 작업 이력. target_table + target_record_id 는 임의 테이블을 가리키는 다형 참조라 FK 를 걸 수 없다
-- (그래서 대상 행이 지워져도 이력은 남는다 — 감사 로그로서는 그게 맞는 동작이다).
CREATE TABLE audit_log
(
    log_id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    action_type      VARCHAR(20) NOT NULL,
    target_table     VARCHAR(50) DEFAULT NULL,
    target_record_id BIGINT      DEFAULT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (log_id),
    KEY idx_audit_log_user_created (user_id, created_at),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE notification
(
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    report_id       BIGINT       DEFAULT NULL,
    -- 보고서 제목 사본. 원본 제목이 바뀌어도 발송 당시 문구가 보존되도록 스냅샷으로 들고 있다.
    report_title    VARCHAR(200) DEFAULT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at         DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (notification_id),
    -- '내 안 읽은 알림' 이 기본 질의다.
    KEY idx_notification_user_read (user_id, is_read),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_notification_report FOREIGN KEY (report_id) REFERENCES report (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- 트랜잭셔널 아웃박스: 도메인 변경과 이벤트 발행을 한 트랜잭션에 묶고, 실제 발행은 별도 릴레이가 한다.
CREATE TABLE outbox_event
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   VARCHAR(50) NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSON        NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 릴레이가 '미발행 건을 발생 순서대로' 폴링한다.
    KEY idx_outbox_event_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
