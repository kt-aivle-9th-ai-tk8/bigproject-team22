-- report/inspection 조정(maintenancereporting BC). V5(report·inspection)·V7(테이블 단수화) 뒤에 얹는 후속 마이그레이션이다.
-- 원칙: 이미 적용된 마이그레이션은 수정하지 않고(체크섬 보호) 다음 버전 파일로 ALTER 한다.
--
-- 하는 일:
--   1) report_type / status 의 실제 저장값 표기(주석) 정정 — 엔티티는 대문자 enum 문자열로 저장한다.
--      status 는 엔티티가 non-null 을 보장하므로 DB 도 NOT NULL 로 조여 외부/직접 SQL 우회를 막는다.
--   2) approver_id 를 '생성요청자(created_by)'로 재사용함을 명시(컬럼명은 당분간 그대로).
--   3) 이상 이벤트 삭제 시 보고서는 남기고 링크만 끊도록 FK 에 ON DELETE SET NULL.
--   4) 보고서 삭제 시 연결된 점검(inspection)은 orphan 으로 남긴다(팀 결정) — report_id 를 nullable 로 열고
--      FK 에 ON DELETE SET NULL 을 건다.


-- 1. 값 표기 정정 + status NOT NULL -------------------------------------------------
-- @Enumerated(STRING) 은 자바 상수명을 그대로 저장하므로 실제 값은 대문자다(Role/UserStatus 관례와 동일).
-- 원 주석이 소문자라 계약이 갈릴 수 있어 바로잡는다(값은 손대지 않고 COMMENT 만 교체).
ALTER TABLE report
    MODIFY COLUMN report_type VARCHAR(50) NOT NULL
        COMMENT 'WIND_FARM_OPERATION / TURBINE_OPERATION / DEFECT_DIAGNOSIS / ANOMALY_EVENT';

-- status: 엔티티(Report.status)가 non-null 을 보장한다. DB 가 NULL 을 허용하면 외부 작성자·직접 SQL 이
-- 이 계약을 우회하므로 DB 에서도 강제한다. 혹시 남아 있는 NULL 행은 접수 상태로 보정한 뒤 조인다.
UPDATE report SET status = 'PENDING' WHERE status IS NULL;
ALTER TABLE report
    MODIFY COLUMN status VARCHAR(50) NOT NULL
        COMMENT 'PENDING(적재만 됨) / PROCESSING(생성중) / GENERATED(생성됨)';


-- 2. approver_id 재사용 명시 -------------------------------------------------------
-- 승인(결재) 기작은 도입하지 않기로 했고, 이 컬럼을 '생성 요청자 id'로 재사용한다.
-- 엔티티는 이를 createdBy 로 매핑한다. 혼동을 줄이기 위해 주석만 갱신한다(컬럼명 개명은 이후 과제).
ALTER TABLE report
    MODIFY COLUMN approver_id BIGINT DEFAULT NULL
        COMMENT '생성 요청자 user_id(엔티티 createdBy 로 매핑). 자동 생성(이상감지)은 NULL';


-- 3. 이상 이벤트 FK 에 ON DELETE SET NULL -------------------------------------------
-- 유발 이벤트가 지워져도 보고서(사람이 열람하는 산출물)는 남기고 링크만 끊는다.
-- 이 동작이 없으면 이벤트 삭제가 참조 보고서 때문에 FK 위반으로 실패한다.
ALTER TABLE report
    DROP FOREIGN KEY fk_report_anomaly_event;
ALTER TABLE report
    ADD CONSTRAINT fk_report_anomaly_event FOREIGN KEY (anomaly_event_id)
        REFERENCES anomaly_event (event_id) ON DELETE SET NULL;


-- 4. 보고서 삭제 시 점검(inspection)은 orphan 으로 보존 --------------------------------
-- 팀 결정: report 를 지워도 원자료인 점검은 남긴다. 그러려면 report_id 가 NULL 이 될 수 있어야 하므로
-- nullable 로 열고 FK 에 ON DELETE SET NULL 을 건다. 이 처리가 없으면 점검이 달린 보고서 삭제가
-- FK 위반으로 실패한다(현재는 전역 핸들러의 catch-all 에 걸려 500). FK 를 잠깐 드롭한 뒤
-- 컬럼을 열고 다시 건다(컬럼 정의 변경 순서 안전).
ALTER TABLE inspection
    DROP FOREIGN KEY fk_inspection_report;
ALTER TABLE inspection
    MODIFY COLUMN report_id BIGINT DEFAULT NULL;
ALTER TABLE inspection
    ADD CONSTRAINT fk_inspection_report FOREIGN KEY (report_id)
        REFERENCES report (report_id) ON DELETE SET NULL;
