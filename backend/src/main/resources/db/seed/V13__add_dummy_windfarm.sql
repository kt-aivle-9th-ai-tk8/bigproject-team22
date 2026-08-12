-- 주의: 화순 U1~U7(turbine_id 7~13)의 2026 SCADA가
-- 선행 seed에서 생성된 이후 실행되어야 한다.

-- ── 1) 더미 발전소 생성 ──

INSERT INTO wind_farm (
    wind_farm_id,
    wind_farm_name,
    wind_farm_latitude,
    wind_farm_longitude,
    capacity,
    installation_date,
    address,
    created_at,
    aws_station_id,
    asos_station_id
) VALUES
(
    3,
    '보성발전소',
    34.9339325118919,
    127.210618569149,
    10,
    '2015-11-01',
    '전남 보성군 ...',
    '2026-08-06 16:30:00.123456',
    751,
    166
),
(
    4,
    '장흥호발전소',
    34.771218,
    126.905353,
    0,
    '2021-11-03',
    '전남 장흥군 유치면 용문리 산6-3',
    '2026-08-06 16:30:00.123456',
    778,
    260
);


-- ── 2) 보성 터빈 7기 생성 ──
-- 화순 U1~U7에 대응하는 더미 터빈

INSERT INTO turbine (
    turbine_id,
    wind_farm_id,
    turbine_model_id,
    turbine_code,
    turbine_latitude,
    turbine_longitude,
    installation_date,
    created_at
) VALUES
(15, 3, 2, 'B1', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(16, 3, 2, 'B2', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(17, 3, 2, 'B3', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(18, 3, 2, 'B4', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(19, 3, 2, 'B5', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(20, 3, 2, 'B6', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456'),
(21, 3, 2, 'B7', 34.9339325118919, 127.210618569149, '2015-11-01', '2026-08-06 16:30:00.123456');

-- ── 3) 혹시 기존 보성 2026 집계/SCADA가 있으면 제거 ──

DELETE FROM daily_generation
WHERE turbine_id BETWEEN 15 AND 21
  AND stat_at >= '2026-01-01 00:00:00'
  AND stat_at < '2027-01-01 00:00:00';

DELETE FROM monthly_generation
WHERE turbine_id BETWEEN 15 AND 21
  AND stat_at >= '2026-01-01 00:00:00'
  AND stat_at < '2027-01-01 00:00:00';

DELETE FROM scada_record
WHERE turbine_id BETWEEN 15 AND 21
  AND recorded_at >= '2026-01-01 00:00:00'
  AND recorded_at < '2027-01-01 00:00:00';


-- ── 4) 화순 U1~U7 → 보성 B1~B7 SCADA 복사 ──

INSERT INTO scada_record (
    turbine_id,
    recorded_at,
    power_output
)
SELECT
    CASE turbine_id
        WHEN 7  THEN 15
        WHEN 8  THEN 16
        WHEN 9  THEN 17
        WHEN 10 THEN 18
        WHEN 11 THEN 19
        WHEN 12 THEN 20
        WHEN 13 THEN 21
    END,
    recorded_at,
    power_output
FROM scada_record
WHERE turbine_id BETWEEN 7 AND 13
  AND recorded_at >= '2026-01-01 00:00:00'
  AND recorded_at < '2027-01-01 00:00:00';


-- ── 5) 보성 일별 발전량 생성 ──

INSERT INTO daily_generation (
    turbine_id,
    stat_at,
    daily_power_output
)
SELECT
    turbine_id,
    DATE(recorded_at),
    SUM(power_output)
FROM scada_record
WHERE turbine_id BETWEEN 15 AND 21
  AND recorded_at >= '2026-01-01 00:00:00'
  AND recorded_at < '2027-01-01 00:00:00'
GROUP BY turbine_id, DATE(recorded_at);


-- ── 6) 보성 월별 발전량 생성 ──

INSERT INTO monthly_generation (
    turbine_id,
    stat_at,
    monthly_power_output
)
SELECT
    turbine_id,
    STR_TO_DATE(
        DATE_FORMAT(recorded_at, '%Y-%m-01'),
        '%Y-%m-%d'
    ),
    SUM(power_output)
FROM scada_record
WHERE turbine_id BETWEEN 15 AND 21
  AND recorded_at >= '2026-01-01 00:00:00'
  AND recorded_at < '2027-01-01 00:00:00'
GROUP BY
    turbine_id,
    STR_TO_DATE(
        DATE_FORMAT(recorded_at, '%Y-%m-01'),
        '%Y-%m-%d'
    );