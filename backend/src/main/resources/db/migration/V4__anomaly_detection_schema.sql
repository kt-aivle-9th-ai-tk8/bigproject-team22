-- 이상감지(anomaly detection) 스키마.
-- 매시각 배치가 SageMaker Serverless Inference 를 호출해 판정 결과를 받고, BE 가 여기에 적재한다.
-- (SageMaker 에는 RDS read 권한만 주고 쓰기는 BE 가 담당한다.)

-- 1) SCADA 원천에 추론이 산출하는 파생 컬럼을 붙인다.
--    엔티티에는 아직 매핑하지 않는다 — 적재 주체(P7)가 들어올 때 쓰기 방식과 함께 정한다.
--    ddl-auto=validate 는 매핑되지 않은 여분 컬럼을 문제 삼지 않으므로 스키마만 선반영해도 안전하고,
--    지금 매핑하면 발전량 조회가 쓰지도 않는 7개 컬럼을 매 행 읽게 된다.
ALTER TABLE scada_record
    ADD COLUMN wind_speed            DOUBLE  DEFAULT NULL COMMENT '나셀 풍속(m/s)',
    ADD COLUMN air_density           DOUBLE  DEFAULT NULL COMMENT '공기밀도(kg/m^3), 계산값',
    ADD COLUMN norm_wind_speed       DOUBLE  DEFAULT NULL COMMENT 'IEC 61400-12-1 기준 보정 풍속',
    ADD COLUMN is_stopped            TINYINT DEFAULT NULL COMMENT '터빈 정지 여부(0/1)',
    ADD COLUMN train_mask            TINYINT DEFAULT NULL COMMENT '정상거동 여부(0/1)',
    ADD COLUMN expected_power_pooled DOUBLE  DEFAULT NULL COMMENT '단지 통합 모델 기대 발전량(kW)',
    ADD COLUMN expected_power_unit   DOUBLE  DEFAULT NULL COMMENT '터빈별 모델 기대 발전량(kW)';

-- 2) 이상 이벤트.
--    수치 컬럼이 모두 nullable 인 이유: 유형·계층마다 산출되는 지표가 다르다.
--    예) data_missing 은 관측 자체가 없어 z_score/expected_power/actual_power/estimated_loss_kwh 가 전부 비고,
--        계층 A 는 z_score·energy_ratio_30d 가, 계층 B 는 일부 순시값이 비어 있다.
CREATE TABLE anomaly_events
(
    event_id           BIGINT      NOT NULL AUTO_INCREMENT,
    -- 단지 단위 사안(scope='farm')도 호기별로 한 행씩 생긴다. scope 는 원인 구분 라벨이지 집계 단위가 아니다.
    turbine_id         BIGINT      NOT NULL,
    start_time         DATETIME(6) NOT NULL COMMENT '이상 감지 시작',
    end_time           DATETIME(6) DEFAULT NULL COMMENT 'NULL 이면 진행 중',
    expected_power     DOUBLE      DEFAULT NULL COMMENT '모델 예측 출력(kW)',
    actual_power       DOUBLE      DEFAULT NULL COMMENT '실제 출력(kW)',
    z_score            DOUBLE      DEFAULT NULL COMMENT '기대-실측 차이의 표준화 값. 계층 A 는 NULL',
    deviation_pct      DOUBLE      DEFAULT NULL COMMENT '기대 대비 편차율',
    -- 대문자 enum 문자열로 저장한다(Role/UserStatus/ReportType 과 동일 관례).
    tier               VARCHAR(20) NOT NULL COMMENT 'A: 급성, B: 만성',
    event_type         VARCHAR(50) NOT NULL COMMENT 'PROLONGED_STOP / DATA_MISSING / CHRONIC_SCREENING / CHRONIC_CONFIRMED',
    scope              VARCHAR(20) DEFAULT NULL COMMENT 'DATA_MISSING 전용. FARM: 수집·통신 장애, TURBINE: 설비 문제',
    energy_ratio_30d   DOUBLE      DEFAULT NULL COMMENT '계층 B 지표(호기 30일 에너지비 - 단지 중앙값). 계층 A 는 NULL',
    estimated_loss_kwh DOUBLE      DEFAULT NULL COMMENT '놓친 발전량(kWh). DATA_MISSING 은 관측이 없어 산출 불가',
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT fk_anomaly_events_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id),
    -- 멱등키. 매시각 배치가 진행 중인 이벤트를 같은 키로 다시 산출하므로(종료 시각과 지표만 갱신된다)
    -- 이 제약이 없으면 회차마다 행이 쌓인다. 분산 락은 최적화일 뿐이고 중복 방지의 실제 근거는 이 제약이다.
    CONSTRAINT uq_anomaly_events_identity UNIQUE (turbine_id, tier, event_type, start_time),
    -- 미해결 이벤트 조회(end_time IS NULL) 와 최신순 열람에 쓴다.
    INDEX idx_anomaly_events_turbine_start (turbine_id, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
