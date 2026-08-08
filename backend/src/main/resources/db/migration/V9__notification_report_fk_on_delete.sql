-- 보고서 삭제 시 알림은 남기고 report 링크만 끊는다(RESTRICT → SET NULL).
-- 알림은 report_title 스냅샷을 들고 있어, 원본 보고서가 지워져도 계속 읽을 수 있다.
-- 이 처리가 없으면 알림이 달린 보고서 삭제가 fk_notification_report(RESTRICT) 위반으로 실패한다.
-- report_id 는 이미 nullable(hana V6) 이라 컬럼 변경은 불필요하고 FK 만 교체한다.
-- (hana V8 다음이라 V9. 이미 적용된 마이그레이션은 수정하지 않는다 — 체크섬 보호.)
ALTER TABLE notification
    DROP FOREIGN KEY fk_notification_report;
ALTER TABLE notification
    ADD CONSTRAINT fk_notification_report FOREIGN KEY (report_id)
        REFERENCES report (report_id) ON DELETE SET NULL;
