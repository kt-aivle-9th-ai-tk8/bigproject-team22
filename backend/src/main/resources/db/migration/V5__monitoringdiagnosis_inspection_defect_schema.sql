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
CREATE TABLE report
(
    report_id    BIGINT       NOT NULL AUTO_INCREMENT,
    wind_farm_id BIGINT       NOT NULL,
    turbine_id   BIGINT       DEFAULT NULL,
    -- 결재자. 승인 전에는 비어 있다.
    approver_id  BIGINT       DEFAULT NULL,
    event_id     BIGINT       DEFAULT NULL COMMENT 'anomaly 보고서의 대상 이벤트(V4 anomaly_events)',
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
    -- V4 가 만든 anomaly_events(복수형)를 참조한다.
    CONSTRAINT fk_report_event FOREIGN KEY (event_id) REFERENCES anomaly_events (event_id)
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
-- 3. 결함 — AI 영상 판독 모델(YOLOv11 + EfficientNet)이 검출·분류한 건.
--    '확정된 손상'이 아니라 검출 결과다.
--    bbox_* 는 image_path 이미지 안의 검출 박스, confidence 는 모델 신뢰도.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE defect
(
    defect_id     BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT       NOT NULL,
    blade_id      BIGINT       NOT NULL,
    defect_type   VARCHAR(50)  NOT NULL COMMENT 'YOLO 모델의 클래스명',
    -- 심각도 1~4. 값 범위 검증은 애플리케이션이 담당한다(등급 확장 시 마이그레이션 불필요).
    severity      INT          NOT NULL,
    -- 앞전/뒷전 등 블레이드 부위.
    part_side     VARCHAR(20)  NOT NULL,
    bbox_x        DOUBLE       DEFAULT NULL,
    bbox_y        DOUBLE       DEFAULT NULL,
    bbox_w        DOUBLE       DEFAULT NULL,
    bbox_h        DOUBLE       DEFAULT NULL,
    area_pixel    DOUBLE       DEFAULT NULL,
    confidence    DOUBLE       DEFAULT NULL COMMENT 'AI 검출 신뢰도',
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
