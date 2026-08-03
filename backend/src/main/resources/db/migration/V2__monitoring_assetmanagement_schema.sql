-- 관제(monitoring) API 그룹용 스키마: assetmanagement / monitoringdiagnosis BC.
-- ddl-auto=validate 이므로 각 엔티티 매핑과 정확히 일치해야 한다.

CREATE TABLE turbine_models
(
    turbine_model_id BIGINT      NOT NULL AUTO_INCREMENT,
    model            VARCHAR(50) NOT NULL,
    manufacturer     VARCHAR(50) DEFAULT NULL,
    rated_power      INT         DEFAULT NULL,
    rotor_diameter   DOUBLE      DEFAULT NULL,
    hub_height       DOUBLE      DEFAULT NULL,
    blade_length     DOUBLE      DEFAULT NULL,
    cut_in_speed     DOUBLE      DEFAULT NULL,
    rated_speed      DOUBLE      DEFAULT NULL,
    cut_out_speed    DOUBLE      DEFAULT NULL,
    PRIMARY KEY (turbine_model_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE wind_farms
(
    wind_farm_id        BIGINT       NOT NULL AUTO_INCREMENT,
    wind_farm_name      VARCHAR(100) NOT NULL,
    wind_farm_latitude  DOUBLE       NOT NULL,
    wind_farm_longitude DOUBLE       NOT NULL,
    capacity            DOUBLE       DEFAULT NULL,
    installation_date   DATE         DEFAULT NULL,
    address             VARCHAR(200) DEFAULT NULL,
    created_at          DATETIME(6)  DEFAULT NULL,
    -- 실시간 날씨 조회 대상 기상청 관측소 식별자(기상청 지점번호)
    aws_station_id      BIGINT       DEFAULT NULL,
    asos_station_id     BIGINT       DEFAULT NULL,
    PRIMARY KEY (wind_farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE turbines
(
    turbine_id        BIGINT      NOT NULL AUTO_INCREMENT,
    wind_farm_id      BIGINT      NOT NULL,
    turbine_model_id  BIGINT      NOT NULL,
    turbine_code      VARCHAR(20) NOT NULL,
    turbine_latitude  DOUBLE      NOT NULL,
    turbine_longitude DOUBLE      NOT NULL,
    installation_date DATE        DEFAULT NULL,
    created_at        DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (turbine_id),
    CONSTRAINT fk_turbines_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farms (wind_farm_id),
    CONSTRAINT fk_turbines_model FOREIGN KEY (turbine_model_id) REFERENCES turbine_models (turbine_model_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE blades
(
    blade_id             BIGINT      NOT NULL AUTO_INCREMENT,
    turbine_id           BIGINT      NOT NULL,
    blade_tag            VARCHAR(10) NOT NULL,
    installation_date    DATE        DEFAULT NULL,
    created_at           DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (blade_id),
    CONSTRAINT fk_blades_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 발전량 원천 데이터. 발전량 조회가 실제 사용하는 컬럼만 부분 생성한다.
CREATE TABLE scada_record
(
    turbine_id   BIGINT      NOT NULL,
    recorded_at  DATETIME(6) NOT NULL,
    power_output DOUBLE      DEFAULT NULL,
    PRIMARY KEY (turbine_id, recorded_at),
    CONSTRAINT fk_scada_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 일별 발전량 집계(cron 롤업, RDS 직접 조회). 복합 PK (turbine_id, stat_at). stat_at 은 해당 일 00:00.
-- 값 타입은 scada_record.power_output 과 동일한 DOUBLE.
CREATE TABLE daily_generation
(
    turbine_id         BIGINT      NOT NULL,
    stat_at            DATETIME(6) NOT NULL,
    daily_power_output DOUBLE      DEFAULT NULL,
    PRIMARY KEY (turbine_id, stat_at),
    CONSTRAINT fk_daily_generation_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 월별 발전량 집계(cron 롤업, RDS 직접 조회). stat_at 은 해당 월 1일 00:00. 복합 PK (turbine_id, stat_at).
CREATE TABLE monthly_generation
(
    turbine_id           BIGINT      NOT NULL,
    stat_at              DATETIME(6) NOT NULL,
    monthly_power_output DOUBLE      DEFAULT NULL,
    PRIMARY KEY (turbine_id, stat_at),
    CONSTRAINT fk_monthly_generation_turbine FOREIGN KEY (turbine_id) REFERENCES turbines (turbine_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 사용자-단지 담당 배정(User N:M WindFarm 교차 테이블). 복합 PK (user_id, wind_farm_id) 로 쌍 유일성 강제.
-- user 기준 목록조회/쌍 존재검사가 PK로 커버되고, 역질의(발전소→유저)는 wind_farm_id FK 자동 인덱스로 커버.
CREATE TABLE assignments
(
    user_id       BIGINT      NOT NULL,
    wind_farm_id  BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, wind_farm_id),
    CONSTRAINT fk_assignments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_wind_farm FOREIGN KEY (wind_farm_id) REFERENCES wind_farms (wind_farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
