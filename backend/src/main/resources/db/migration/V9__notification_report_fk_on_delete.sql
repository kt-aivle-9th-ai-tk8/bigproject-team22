-- notification 조정(P3)

-- 1) 보고서 삭제 시 알림은 남기고 report 링크만 끊는다(RESTRICT → SET NULL).
-- 알림은 report_title 스냅샷을 들고 있어 원본 보고서가 지워져도 계속 읽을 수 있다.
-- report_id 는 이미 nullable 이라 컬럼 변경은 불필요하고 FK 만 교체한다.
ALTER TABLE notification
    DROP FOREIGN KEY fk_notification_report;
ALTER TABLE notification
    ADD CONSTRAINT fk_notification_report FOREIGN KEY (report_id)
        REFERENCES report (report_id) ON DELETE SET NULL;

-- 2) 조회 쿼리(최신순/미읽음) 성능을 위해 sent_at 정렬용 인덱스로 교체한다.
-- hana 의 (user_id, is_read) 는 sent_at 정렬을 못 살려 filesort 를 유발한다. 두 접근 경로를 각각 커버한다:
--   (user_id, sent_at)          → 전체 최신순(주 질의; FE 가 is_read 를 함께 받아 표시)
--   (user_id, is_read, sent_at) → 미읽음 최신순(현재 FE 미사용, 향후 미읽음 전용 뷰 대비)
-- 두 인덱스는 서로 다른 질의를 커버해 중복이 아니다(is_read 가 중간에 낀 인덱스는 전체-최신순 정렬을 못 살린다).
-- 한 ALTER 로 처리 — 새 인덱스가 user_id 를 prefix 로 가져 fk_notification_user 의 backing 이 유지된다.
ALTER TABLE notification
    DROP INDEX idx_notification_user_read,
    ADD INDEX idx_notification_user_sent (user_id, sent_at),
    ADD INDEX idx_notification_user_read_sent (user_id, is_read, sent_at);
-- 참고로, id기반 정렬은 추후 id가 uuid 등 순서를 보장 불가능한 것으로 변경되었을 때를 대비해 사용하지 않고, 명시적 sent_at 기준 정렬을 구현하고자 함.
