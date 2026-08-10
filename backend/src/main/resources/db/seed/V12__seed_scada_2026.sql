-- 2026 SCADA 사전 적재 (운영·로컬 전용, test 프로파일에서는 db/seed 를 제외해 실행되지 않는다).
--
-- 원본 CSV 는 2025-12-31 까지만 있다. "실시간"을 방증하려면 현재 시각(2026)에도 데이터가 있어야 하는데,
-- 배포/다운타임 타이밍과 무관하게 항상 데이터가 존재하도록 2026 을 '미리 전부' 깔아 둔다:
--   * 스텁 스케줄러로 매시각 채우면 배포 전 구간이 비고, 서버 다운 구간에 구멍이 생긴다 → 사전 적재로 원천 제거.
--   * 소스는 이미 DB 에 있다(V11 이 2025 를 적재) → 새 데이터 파일 없이 INSERT...SELECT 로 유도한다.
--     2025·2026 모두 평년(365일)이라 recorded_at + INTERVAL 1 YEAR 가 1:1 로 대응한다(2/29 없음).
--   * raw(power_output)만 복사한다. expected_power·이상탐지는 P7 cron 이 2026 에 대해 '실제로' 수행한다
--     (데이터만 2025 것을 재활용). 그래서 cron 은 recorded_at <= now 로 게이트해 미래 구간을 backfill 하지 않는다.
--
-- 재실행 안전: 각 구간을 먼저 비우고 다시 넣는다(부분 실패 후 재적용 대비). 신규 적용 시엔 2026 이 없어 no-op.
-- 롤업은 raw 에서 SUM(에너지). only_full_group_by(MySQL8) 대응으로 SELECT/GROUP BY 표현식을 동일하게 쓴다.

-- 1) raw: 2025 → 2026 복사
DELETE FROM scada_record
WHERE recorded_at >= '2026-01-01 00:00:00' AND recorded_at < '2027-01-01 00:00:00';
INSERT INTO scada_record (turbine_id, recorded_at, power_output)
SELECT turbine_id, recorded_at + INTERVAL 1 YEAR, power_output
FROM scada_record
WHERE recorded_at >= '2025-01-01 00:00:00' AND recorded_at < '2026-01-01 00:00:00';

-- 2) 일별 발전량(2026)
DELETE FROM daily_generation
WHERE stat_at >= '2026-01-01 00:00:00' AND stat_at < '2027-01-01 00:00:00';
INSERT INTO daily_generation (turbine_id, stat_at, daily_power_output)
SELECT turbine_id, DATE(recorded_at), SUM(power_output)
FROM scada_record
WHERE recorded_at >= '2026-01-01 00:00:00' AND recorded_at < '2027-01-01 00:00:00'
GROUP BY turbine_id, DATE(recorded_at);

-- 3) 월별 발전량(2026)
DELETE FROM monthly_generation
WHERE stat_at >= '2026-01-01 00:00:00' AND stat_at < '2027-01-01 00:00:00';
INSERT INTO monthly_generation (turbine_id, stat_at, monthly_power_output)
SELECT turbine_id,
       STR_TO_DATE(DATE_FORMAT(recorded_at, '%Y-%m-01'), '%Y-%m-%d'),
       SUM(power_output)
FROM scada_record
WHERE recorded_at >= '2026-01-01 00:00:00' AND recorded_at < '2027-01-01 00:00:00'
GROUP BY turbine_id, STR_TO_DATE(DATE_FORMAT(recorded_at, '%Y-%m-01'), '%Y-%m-%d');
