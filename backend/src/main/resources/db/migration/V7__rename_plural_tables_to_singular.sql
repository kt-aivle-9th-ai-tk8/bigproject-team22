-- 복수형 테이블명을 ERD·팀규약대로 전면 단수화한다.
-- 이미 적용된 V1~V6 는 불변(체크섬 보호)이므로, 개명은 이 forward 마이그레이션 하나로만 수행한다.
--
-- 대상 7개: users→`user`, turbines→turbine, wind_farms→wind_farm, turbine_models→turbine_model,
--           blades→blade, assignments→assignment, anomaly_events→anomaly_event.
-- 컬럼명(user_id/turbine_id/wind_farm_id/...)과 이미 단수인 이름(scada_record, report, inspection,
-- defect, notification, ... 및 role 기반 제약명 fk_*_wind_farm/fk_*_user/fk_defect_blade 등)은 대상 아님.
--
-- 실측(MySQL 8.4)으로 확정한 사실:
--   * RENAME TABLE 은 자식 테이블 FK 의 '참조 테이블명'을 자동 갱신한다(child FK 이름은 이미 단수 role 이라 유지).
--   * 그러나 index/constraint '이름'은 자동 개명되지 않는다 → 아래에서 수동 개명.
--   * fk_turbines_wind_farm/fk_blades_turbine/fk_assignments_user/fk_anomaly_events_turbine 는 자체 인덱스가
--     없이 UNIQUE/PK 를 backing 으로 재사용한다 → FK 만 drop+add(재추가 시 기존 인덱스를 다시 재사용).
--   * fk_turbines_model/fk_assignments_wind_farm 만 동명 자체 인덱스를 가진다 → 인덱스도 함께 개명.
--   * `user` 는 예약어라 백틱으로 인용한다.

-- 1) 테이블 원자적 개명. 한 문장이라 자식 FK 참조가 일괄 자동 갱신된다(개명 순서 제약 없음).
RENAME TABLE
    users TO `user`,
    turbines TO turbine,
    wind_farms TO wind_farm,
    turbine_models TO turbine_model,
    blades TO blade,
    assignments TO assignment,
    anomaly_events TO anomaly_event;

-- 2) 이름에 옛 복수형이 박힌 index 개명(RENAME INDEX = 메타데이터, in-place).
ALTER TABLE `user`        RENAME INDEX uk_users_employee_id            TO uk_user_employee_id;
ALTER TABLE turbine       RENAME INDEX uq_turbines_farm_code           TO uq_turbine_farm_code;
ALTER TABLE turbine       RENAME INDEX uq_turbines_id_wind_farm        TO uq_turbine_id_wind_farm;
ALTER TABLE turbine       RENAME INDEX fk_turbines_model               TO fk_turbine_model;
ALTER TABLE blade         RENAME INDEX uq_blades_turbine_tag           TO uq_blade_turbine_tag;
ALTER TABLE assignment    RENAME INDEX fk_assignments_wind_farm        TO fk_assignment_wind_farm;
ALTER TABLE anomaly_event RENAME INDEX uq_anomaly_events_identity      TO uq_anomaly_event_identity;
ALTER TABLE anomaly_event RENAME INDEX idx_anomaly_events_turbine_start TO idx_anomaly_event_turbine_start;
ALTER TABLE anomaly_event RENAME INDEX idx_anomaly_events_ongoing      TO idx_anomaly_event_ongoing;

-- 3) FK constraint 이름 개명. MySQL 은 FK RENAME 미지원 → drop+add.
--    정의(컬럼·참조·삭제규칙)는 V1~V5 원본 그대로이며 참조 테이블만 신 이름이다.
--    자체 인덱스가 없는 FK 는 재추가 시 UNIQUE/PK 를 다시 backing 으로 재사용하므로 중복 인덱스가 생기지 않는다.
ALTER TABLE turbine
    DROP FOREIGN KEY fk_turbines_wind_farm,
    ADD CONSTRAINT fk_turbine_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farm (wind_farm_id);
ALTER TABLE turbine
    DROP FOREIGN KEY fk_turbines_model,
    ADD CONSTRAINT fk_turbine_model FOREIGN KEY (turbine_model_id) REFERENCES turbine_model (turbine_model_id);
ALTER TABLE blade
    DROP FOREIGN KEY fk_blades_turbine,
    ADD CONSTRAINT fk_blade_turbine FOREIGN KEY (turbine_id) REFERENCES turbine (turbine_id);
ALTER TABLE assignment
    DROP FOREIGN KEY fk_assignments_user,
    ADD CONSTRAINT fk_assignment_user FOREIGN KEY (user_id) REFERENCES `user` (user_id);
ALTER TABLE assignment
    DROP FOREIGN KEY fk_assignments_wind_farm,
    ADD CONSTRAINT fk_assignment_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farm (wind_farm_id);
ALTER TABLE anomaly_event
    DROP FOREIGN KEY fk_anomaly_events_turbine,
    ADD CONSTRAINT fk_anomaly_event_turbine FOREIGN KEY (turbine_id) REFERENCES turbine (turbine_id);
