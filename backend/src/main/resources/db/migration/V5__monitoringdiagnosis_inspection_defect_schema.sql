-- monitoringdiagnosis BC 스키마: 드론 점검(inspection)·결함(defect)·보고서(report) + 기상 관측.
--
-- V4(anomaly_detection)와 역할 분담:
--   V4 : scada_record 파생 컬럼 보강 + anomaly_events(이상 감지 이벤트)
--   V5 : 점검·결함·보고서 + aws_record/asos_record(기상 관측)
--   두 파일이 scada_record 를 중복으로 ALTER 하지 않도록, 컬럼 보강은 V4 에만 둔다.
--
-- 명명 규칙 주의: V2 가 만든 참조 테이블은 복수형(turbines/blades/wind_farms/turbine_models)이고
--   ERD 와 Spring 기본 네이밍은 단수형이다. 아래 신규 테이블은 ERD 를 따라 단수형으로 만든다.
--   (V4 의 anomaly_events 는 복수형이라 혼재가 남지만, 이미 develop 에 올라가 있어 건드리지 않는다.)
--
-- ddl-auto=validate 는 '엔티티가 요구하는 컬럼이 있는지'만 보고 여분의 테이블·컬럼은 문제 삼지
-- 않으므로, 대응 엔티티가 아직 없는 테이블을 먼저 만들어도 기동에 영향이 없다.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. 보고서 — report-agent-service 가 생성한 결과물의 저장소.
--    report_type 별로 채워지는 FK 가 다르다(전부 nullable 인 이유):
--      defect         : turbine_id 는 점검이 여러 대를 볼 수 있어 비고, inspection 이 report_id 로 매단다
--      operation      : turbine_id 로 대상 터빈 지정
--      farm_operation : wind_farm_id 만
--      anomaly        : event_id 로 대상 이벤트 지정
-- ─────────────────────────────────────────────────────────────────────────────
-- report 의 복합 FK 가 참조할 대상. turbine_id 는 이미 PK 라 유일하지만, (turbine_id, wind_farm_id)
-- 순서의 인덱스가 있어야 복합 FK 가 이를 참조할 수 있다(turbines 는 V2 라 여기서 ALTER).
-- 실질적으로 turbine_id 가 채워지는 운영 보고서(TURBINE_OPERATION)에서만 소속 검사가 걸린다 —
-- defect/farm_operation 보고서는 turbine_id 가 NULL 이라 MATCH SIMPLE 로 건너뛴다.
ALTER TABLE turbines
    ADD CONSTRAINT uq_turbines_id_wind_farm UNIQUE (turbine_id, wind_farm_id);

CREATE TABLE report
(
    report_id        BIGINT       NOT NULL AUTO_INCREMENT,
    wind_farm_id     BIGINT       NOT NULL,
    turbine_id       BIGINT       DEFAULT NULL,
    -- 결재자. 승인 전에는 비어 있다.
    approver_id      BIGINT       DEFAULT NULL,
    anomaly_event_id BIGINT       DEFAULT NULL COMMENT '자동 생성 트리거가 된 이벤트. 운영/정기 보고서는 NULL',
    report_type      VARCHAR(50)  NOT NULL COMMENT 'wind_farm_operation / turbine_operation / defect_diagnosis / anomaly_event',
    -- 보고서가 다루는 구간. DATE 가 아니라 DATETIME 인 이유: 대상이 시각 단위로 발생한다.
    --   anomaly : anomaly_events.start_time/end_time (매시각 배치)
    --   defect  : inspection_start/end — 점검이 자정을 넘겨 이틀에 걸치는 건이 있다
    -- 날짜 단위 보고서(월간 운영 등)는 00:00:00 으로 채우면 되므로 손해가 없다.
    period_start     DATETIME(6)  NOT NULL,
    period_end       DATETIME(6)  NOT NULL,
    title            VARCHAR(200) DEFAULT NULL,
    -- LangGraph 에이전트가 생성한 본문(마크다운).
    -- TEXT(65,535바이트)가 아니라 MEDIUMTEXT(16,777,215바이트)인 이유:
    --   TEXT 는 utf8mb4 기준 한글 약 21,800자(글자당 3바이트, 이모지는 4바이트)에서 잘린다.
    --   결함 보고서는 검출 이미지 수만큼 줄이 늘어나고 image_path(S3 URL)가 길어질 수 있어
    --   상한을 예측할 수 없다. MEDIUMTEXT 면 한글 약 550만 자로 사실상 제약이 없다.
    -- 저장 용량 기준: 1건당 16MB. 현재 실측은 결함 보고서 1~4KB 수준.
    context          MEDIUMTEXT,
    status           VARCHAR(50)  DEFAULT NULL COMMENT 'pending: 적재만 됨 / processing: 생성중 / generated: 생성됨',
    generated_at     DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (report_id),
    KEY idx_report_farm_type_period (wind_farm_id, report_type, period_start),
    CONSTRAINT fk_report_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farms (wind_farm_id),
    -- 터빈 존재 + 소속 단지 일치를 한 번에 강제하는 복합 FK(교차 컬럼 정합성).
    -- (turbine_id, wind_farm_id) 가 turbines 에 실재해야 하므로 "터빈이 이 단지 소속인가"가 DB 로 보장된다.
    -- turbine_id 가 NULL(defect/farm_operation 보고서)이면 표준 MATCH SIMPLE 규칙상 이 FK 는 건너뛰고,
    -- 단지 존재는 위의 fk_report_wind_farm 이 검사한다. 단일 FK 로는 교차 컬럼 일치를 표현할 수 없다.
    CONSTRAINT fk_report_turbine_in_farm FOREIGN KEY (turbine_id, wind_farm_id)
        REFERENCES turbines (turbine_id, wind_farm_id),
    CONSTRAINT fk_report_approver FOREIGN KEY (approver_id) REFERENCES users (user_id),
    -- V4 가 만든 anomaly_events(복수형)를 참조한다.
    CONSTRAINT fk_report_anomaly_event FOREIGN KEY (anomaly_event_id) REFERENCES anomaly_events (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. 드론 점검 — 터빈 1대당 1건. 드론 1회 출동으로 2대를 점검하면
--    inspection 2건이 같은 report_id 를 공유한다(보고서 1건 = 점검 N건).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE inspection
(
    inspection_id    BIGINT      NOT NULL AUTO_INCREMENT,
    turbine_id       BIGINT      NOT NULL,
    -- 점검 수행자. ERD 는 INT 지만 users.user_id 가 BIGINT 라 FK 타입을 맞춰야 한다
    -- (타입이 다르면 FK 생성 자체가 실패한다).
    user_id          BIGINT      DEFAULT NULL,
    report_id        BIGINT      NOT NULL,
    inspection_start DATETIME(6) NOT NULL COMMENT '드론 촬영 날짜',
    inspection_end   DATETIME(6) NOT NULL,
    -- UPLOADING → INSPECTING → INSPECTED 진행 순서. 값 검증은 애플리케이션이 담당.
    status           VARCHAR(50) NOT NULL,
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
-- 3. 결함 — AI 영상 판독 모델(YOLOv11 + EfficientNet)이 검출·분류한 건.
--    '확정된 손상'이 아니라 검출 결과다.
--    bbox_* 는 image_path 이미지 안의 검출 박스, confidence 는 모델 신뢰도.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE defect
(
    defect_id     BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT       NOT NULL,
    blade_id      BIGINT       NOT NULL,
    defect_type   VARCHAR(50)  NOT NULL COMMENT 'YOLO 모델의 클래스명 (9종: Contamination, Paint Damage 등)',
    -- 심각도 1~4 (CNN 분류). 값 범위 검증은 애플리케이션이 담당한다(등급 확장 시 마이그레이션 불필요).
    severity      INT          DEFAULT NULL,
    part_side     VARCHAR(20)  DEFAULT NULL COMMENT 'LE / TE / PS / SS',
    bbox_x        DOUBLE       DEFAULT NULL,
    bbox_y        DOUBLE       DEFAULT NULL,
    bbox_w        DOUBLE       DEFAULT NULL,
    bbox_h        DOUBLE       DEFAULT NULL,
    confidence    DOUBLE       DEFAULT NULL COMMENT 'AI 검출 신뢰도 0~1',
    -- S3 키 또는 상대 경로. 렌더링 시 IMAGE_BASE_URL 을 앞에 붙인다.
    image_path    VARCHAR(500) DEFAULT NULL,
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
-- 4. 기상 관측 — 기상청 지점(station) 단위 시계열. 단지가 아니라 관측소가 키다
--    (wind_farms.aws_station_id / asos_station_id 가 이 지점을 가리킨다).
--    관측소는 외부(기상청) 식별자라 FK 를 걸지 않는다 — 단지에 매핑되지 않은 지점도 적재될 수 있다.
--    시각 컬럼명은 scada_record.recorded_at 과 맞춘다.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE aws_record
(
    aws_station_id BIGINT      NOT NULL,
    recorded_at    DATETIME(6) NOT NULL,
    temperature    DOUBLE DEFAULT NULL COMMENT '기온(°C)',
    pressure       DOUBLE DEFAULT NULL COMMENT '기압(hPa)',
    humidity       DOUBLE DEFAULT NULL COMMENT '습도(%)',
    wind_direction DOUBLE DEFAULT NULL COMMENT '풍향(deg)',
    precipitation  DOUBLE DEFAULT NULL COMMENT '강수량(mm)',
    PRIMARY KEY (aws_station_id, recorded_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 적설 관측(ASOS).
CREATE TABLE asos_record
(
    asos_station_id BIGINT      NOT NULL,
    recorded_at     DATETIME(6) NOT NULL,
    sd_hr3          DOUBLE DEFAULT NULL COMMENT '3시간 신적설(cm)',
    sd_day          DOUBLE DEFAULT NULL COMMENT '일 신적설(cm)',
    sd_tot          DOUBLE DEFAULT NULL COMMENT '적설(cm)',
    PRIMARY KEY (asos_station_id, recorded_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
