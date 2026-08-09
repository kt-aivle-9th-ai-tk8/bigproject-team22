-- notification 조정(P3). hana V6(notification) 위에 얹는다. hana V8 다음이라 V9.
-- 이미 적용된 마이그레이션(V1~V8)은 수정하지 않고 여기서 ALTER 한다(체크섬 보호).

-- 1) 보고서 삭제 시 알림은 남기고 report 링크만 끊는다(RESTRICT → SET NULL).
-- 알림은 report_title 스냅샷을 들고 있어 원본 보고서가 지워져도 계속 읽을 수 있다.
-- 이 처리가 없으면 알림이 달린 보고서 삭제가 fk_notification_report(RESTRICT) 위반으로 실패한다.
-- report_id 는 이미 nullable(hana V6) 이라 컬럼 변경은 불필요하고 FK 만 교체한다.
ALTER TABLE notification
    DROP FOREIGN KEY fk_notification_report;
ALTER TABLE notification
    ADD CONSTRAINT fk_notification_report FOREIGN KEY (report_id)
        REFERENCES report (report_id) ON DELETE SET NULL;

-- 2) 인덱스 정리. 실사용 질의는 '내 알림 전체를 최신순으로'(FE 가 is_read 를 함께 받아 표시) 하나뿐이라
-- 정렬을 커버하는 (user_id, sent_at) 로 간다. hana 의 (user_id, is_read) 는 안 읽은 것만 조회하는 경로가
-- 없어(미사용) 죽은 인덱스이므로 제거한다. (DESC 정렬은 오름차순 인덱스 backward scan 으로 처리 — MySQL 8.)
-- 순서 주의: fk_notification_user 가 user_id 인덱스를 요구하므로, 새 인덱스를 먼저 만들어 backing 을 확보한 뒤
-- 기존 (user_id, is_read) 를 제거한다.
CREATE INDEX idx_notification_user_sent_at ON notification (user_id, sent_at);
DROP INDEX idx_notification_user_read ON notification;
