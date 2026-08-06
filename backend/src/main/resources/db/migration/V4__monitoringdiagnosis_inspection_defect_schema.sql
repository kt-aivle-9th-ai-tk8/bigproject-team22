-- monitoringdiagnosis BC 스키마: 드론 점검(inspection)·결함(defect)·이상감지(anomaly_event)·보고서(report)
-- + 기상 관측(aws_record/asos_record) + scada_record 컬럼 보강.
--
-- 배경: V2 는 "발전량 조회가 실제 사용하는 컬럼만" scada_record 를 부분 생성했다. 그래서 풍속·정지여부·
--       기대발전량이 없고, 점검/결함/이상감지 계열은 테이블 자체가 없어 ERD 와 어긋나 있었다.
--       report-agent-service 가 보고서를 만들 때 읽는 데이터가 전부 여기 모여 있다.
--
-- 명명 규칙 주의: V2 가 만든 참조 테이블은 복수형(turbines/blades/wind_farms/turbine_models)이고
--       ERD 와 Spring 기본 네이밍은 단수형이다. 아래 신규 테이블은 ERD 를 따라 단수형으로 만든다
--       (엔티티를 새로 쓸 때 @Table(name=...) 없이 그대로 매핑되는 쪽). 기존 복수형은 이미 배포된
--       스키마라 건드리지 않는다 — 혼재가 싫으면 별도 마이그레이션에서 한 번에 정리할 것.
--
-- ddl-auto=validate 는 '엔티티가 요구하는 컬럼이 있는지'만 보고 여분의 테이블·컬럼은 문제 삼지
-- 않으므로, 대응 엔티티가 아직 없는 테이블을 먼저 만들어도 기동에 영향이 없다.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. scada_record 컬럼 보강 (테이블은 V2 에 이미 있음 — PK(turbine_id, recorded_at) 유지)
--    기대발전량 2종은 LightGBM 서빙(serving/ml)이 채우는 값이다:
--      expected_power_pooled : 단지 통합 모델 예측
--      expected_power_unit   : 터빈별 개별 모델 예측
--    norm_wind_speed 는 IEC 61400-12-1 밀도보정풍속(모델 입력값), air_density 는 그 산출에 쓴 공기밀도.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE scada_record
    ADD COLUMN wind_speed            DOUBLE  DEFAULT NULL AFTER recorded_at,
    ADD COLUMN air_density           DOUBLE  DEFAULT NULL AFTER power_output,
    ADD COLUMN norm_wind_speed       DOUBLE  DEFAULT NULL AFTER air_density,
    -- 정지 여부/학습셋 포함 여부. 0/1 플래그라 TINYINT.
    ADD COLUMN is_stopped            TINYINT DEFAULT NULL AFTER norm_wind_speed,
    ADD COLUMN train_mask            TINYINT DEFAULT NULL AFTER is_stopped,
    ADD COLUMN expected_power_pooled DOUBLE  DEFAULT NULL AFTER train_mask,
    ADD COLUMN expected_power_unit   DOUBLE  DEFAULT NULL AFTER expected_power_pooled;


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. 이상 감지 이벤트 — SCADA 이상 탐지 파이프라인이 생성한다.
--    터빈은 turbine_id FK 로 잡는다. turbine_code 는 단지 안에서만 유일하므로
--    (uq_turbines_farm_code) 코드 단독으로는 터빈이 특정되지 않는다.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE anomaly_event
(
    event_id           BIGINT      NOT NULL AUTO_INCREMENT,
    turbine_id         BIGINT      NOT NULL,
    start_time         DATETIME(6) NOT NULL,
    -- NULL = 아직 해소되지 않은 진행 중 이벤트. 보고서의 '유지 중' 판정 근거다.
    end_time           DATETIME(6)  DEFAULT NULL,
    expected_power     DOUBLE       DEFAULT NULL,
    actual_power       DOUBLE       DEFAULT NULL,
    z_score            DOUBLE       DEFAULT NULL,
    deviation_pct      DOUBLE       DEFAULT NULL,
    -- 감지 계층(tier)과 유형(event_type: prolonged_stop/data_missing/chronic_screening/chronic_confirmed).
    -- 유형이 늘어도 마이그레이션 없이 확장되도록 ENUM 이 아닌 VARCHAR 로 둔다(users.role 과 같은 방침).
    tier               VARCHAR(20)  DEFAULT NULL,
    event_type         VARCHAR(50)  DEFAULT NULL,
    scope              VARCHAR(20)  DEFAULT NULL,
    energy_ratio_30d   DOUBLE       DEFAULT NULL,
    estimated_loss_kwh DOUBLE       DEFAULT NULL,
    created_at         DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (event_id),
    -- 보고서·대시보드의 주 질의가 '터빈 + 기간'이라 복합 인덱스로 커버한다.
    KEY idx_anomaly_event_turbine_start (turbine_id, start_time),
    CONSTRAINT fk_anomaly_event_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. 보고서 — report-agent-service 가 생성한 결과물의 저장소.
--    report_type 별로 채워지는 FK 가 다르다(전부 nullable 인 이유):
--      defect         : turbine_id 는 점검이 여러 대를 볼 수 있어 비고, inspection 이 report_id 로 매단다
--      operation      : turbine_id 로 대상 터빈 지정
--      farm_operation : wind_farm_id 만
--      anomaly        : event_id 로 대상 이벤트 지정
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE report
(
    report_id    BIGINT       NOT NULL AUTO_INCREMENT,
    wind_farm_id BIGINT       NOT NULL,
    turbine_id   BIGINT       DEFAULT NULL,
    -- 결재자. 승인 전에는 비어 있다.
    approver_id  BIGINT       DEFAULT NULL,
    event_id     BIGINT       DEFAULT NULL,
    report_type  VARCHAR(50)  NOT NULL,
    period_start DATE         NOT NULL,
    period_end   DATE         DEFAULT NULL,
    title        VARCHAR(200) DEFAULT NULL,
    -- 생성된 보고서 본문(마크다운). 길이 상한이 없어야 하므로 TEXT.
    content      TEXT,
    status       VARCHAR(50)  DEFAULT NULL,
    generated_at DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (report_id),
    KEY idx_report_farm_type_period (wind_farm_id, report_type, period_start),
    CONSTRAINT fk_report_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farms (wind_farm_id),
    CONSTRAINT fk_report_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id),
    CONSTRAINT fk_report_approver FOREIGN KEY (approver_id) REFERENCES users (user_id),
    CONSTRAINT fk_report_event FOREIGN KEY (event_id) REFERENCES anomaly_event (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. 드론 점검 — 터빈 1대당 1건. 드론 1회 출동으로 2대를 점검하면
--    inspection 2건이 같은 report_id 를 공유한다(보고서 1건 = 점검 N건).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE inspection
(
    inspection_id    BIGINT      NOT NULL AUTO_INCREMENT,
    turbine_id       BIGINT      NOT NULL,
    -- 점검 수행자.
    user_id          BIGINT      NOT NULL,
    report_id        BIGINT      NOT NULL,
    inspection_start DATETIME(6) NOT NULL,
    -- 판독이 끝나기 전에는 비어 있다.
    inspection_end   DATETIME(6) DEFAULT NULL,
    -- UPLOADING → INSPECTING → INSPECTED 진행 순서. 값 검증은 애플리케이션이 담당.
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (inspection_id),
    -- 보고서 1건에 달린 점검 전부를 긁는 질의가 기본 경로다.
    KEY idx_inspection_report (report_id),
    KEY idx_inspection_turbine_start (turbine_id, inspection_start),
    CONSTRAINT fk_inspection_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id),
    CONSTRAINT fk_inspection_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_inspection_report FOREIGN KEY (report_id) REFERENCES report (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. 결함 — AI 영상 판독 모델이 검출한 건. '확정된 손상'이 아니라 검출 결과다.
--    bbox_* 는 image_path 이미지 안의 검출 박스, confidence 는 모델 신뢰도.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE defect
(
    defect_id     BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT       NOT NULL,
    blade_id      BIGINT       NOT NULL,
    defect_type   VARCHAR(50)  NOT NULL,
    -- 심각도 1~4. 값 범위 검증은 애플리케이션이 담당한다(등급 확장 시 마이그레이션 불필요).
    severity      INT          NOT NULL,
    -- 앞전/뒷전 등 블레이드 부위.
    part_side     VARCHAR(20)  NOT NULL,
    bbox_x        DOUBLE       DEFAULT NULL,
    bbox_y        DOUBLE       DEFAULT NULL,
    bbox_w        DOUBLE       DEFAULT NULL,
    bbox_h        DOUBLE       DEFAULT NULL,
    area_pixel    DOUBLE       DEFAULT NULL,
    confidence    DOUBLE       DEFAULT NULL,
    -- S3 키 또는 상대 경로. 렌더링 시 IMAGE_BASE_URL 을 앞에 붙인다.
    image_path    VARCHAR(300) DEFAULT NULL,
    created_at    DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (defect_id),
    -- 보고서가 '점검 1건의 결함 전부'를 긁는 것이 기본 경로다.
    KEY idx_defect_inspection (inspection_id),
    KEY idx_defect_blade (blade_id),
    CONSTRAINT fk_defect_inspection FOREIGN KEY (inspection_id) REFERENCES inspection (inspection_id),
    CONSTRAINT fk_defect_blade FOREIGN KEY (blade_id) REFERENCES blades (blade_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ─────────────────────────────────────────────────────────────────────────────
-- 6. 기상 관측 — 기상청 지점(station) 단위 시계열. 단지가 아니라 관측소가 키다
--    (wind_farms.aws_station_id / asos_station_id 가 이 지점을 가리킨다).
--    관측소는 외부(기상청) 식별자라 FK 를 걸지 않는다 — 단지에 매핑되지 않은 지점도 적재될 수 있다.
--    `timestamp` 는 MySQL 키워드와 겹쳐 백틱으로 감싼다(컬럼명은 CSV/ERD 를 따른다).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE aws_record
(
    aws_station_id BIGINT      NOT NULL,
    `timestamp`    DATETIME(6) NOT NULL,
    temperature    DOUBLE DEFAULT NULL,
    pressure       DOUBLE DEFAULT NULL,
    humidity       DOUBLE DEFAULT NULL,
    wind_direction DOUBLE DEFAULT NULL,
    precipitation  DOUBLE DEFAULT NULL,
    PRIMARY KEY (aws_station_id, `timestamp`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 적설 관측(ASOS). ad_hr3 = 3시간 신적설, ad_day = 일 신적설, ad_tot = 적설.
CREATE TABLE asos_record
(
    asos_station_id BIGINT      NOT NULL,
    `timestamp`     DATETIME(6) NOT NULL,
    ad_hr3          DOUBLE DEFAULT NULL,
    ad_day          DOUBLE DEFAULT NULL,
    ad_tot          DOUBLE DEFAULT NULL,
    PRIMARY KEY (asos_station_id, `timestamp`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
