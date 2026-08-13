package db.seed;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.zip.GZIPInputStream;

/**
 * SCADA 시드 적재의 공용 조각. Flyway 마이그레이션이 아니다(V 접두어가 없어 스캔되지 않는다).
 * <p>
 * {@code V11}(신규 DB 최초 적재)과 {@code V14}(이미 적재된 DB 재적재)가 같은 로직을 써야 하므로
 * 여기 모아 둔다. 두 마이그레이션이 각자 복사본을 들고 있으면 한쪽만 고쳐져 데이터가 갈라진다.
 * <p>
 * 각 메서드는 <b>순서 의존</b>이 있다. {@link #copy2025To2026}는 {@link #loadCsv} 뒤에,
 * {@link #deriveBoseong}은 2026 구간이 만들어진 뒤에 불러야 한다.
 */
final class ScadaSeed {

    static final String RESOURCE = "db/seed/scada_hourly.csv.gz";
    private static final int BATCH_SIZE = 5_000;

    private ScadaSeed() {
    }

    /**
     * raw 와 파생 집계를 모두 비운다. <b>신규 DB 최초 적재(V11) 전용</b> — 그 시점엔 이미 비어 있어 no-op 이다.
     * 이미 데이터가 있는 DB 에서는 절대 쓰지 말 것({@link #deleteDerivedAggregates} 참고).
     */
    static void deleteAll(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM monthly_generation");
            st.executeUpdate("DELETE FROM daily_generation");
            st.executeUpdate("DELETE FROM scada_record");
        }
    }

    /**
     * 파생 집계만 비운다. <b>scada_record 는 지우지 않는다.</b>
     * <p>
     * {@code V4__anomaly_detection_schema.sql} 이 scada_record 에 붙인 6개 컬럼
     * — {@code air_density}, {@code norm_wind_speed}, {@code is_stopped}, {@code train_mask},
     * {@code expected_power_pooled}, {@code expected_power_unit} — 은 추론 산출물이고
     * 이 리포지토리에 재생성 경로가 없다. raw 를 지우면 그 6개가 함께 영구 소실된다.
     * (소비자는 실재한다 — report-agent 의 datasource, anomaly 서비스의 계층 A/B 판정.)
     * <p>
     * daily/monthly 는 raw 에서 100% 재생성되므로 지워도 무손실이다.
     */
    static void deleteDerivedAggregates(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM monthly_generation");
            st.executeUpdate("DELETE FROM daily_generation");
        }
    }

    /**
     * 번들된 gzip CSV → scada_record. 장흥(1~6) · 화순(7~14) 의 2015~2025 구간이다.
     * <p>
     * CSV: {@code turbine_id,recorded_at,power_output,wind_speed} (헤더 없음, 임베디드 콤마 없음).
     * 4열이 아니면 예외로 즉시 중단한다 — 옛 3열 아티팩트를 관용하면 조용한 오적재가 된다.
     * 값이 빈 칸이면 NULL 로 넣는다 — 0 이 아니라 '없음'이고, 그 구분이 결측 판정의 근거다.
     */
    static long loadCsv(Connection conn) throws Exception {
        InputStream in = ScadaSeed.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + RESOURCE
                    + " (src/main/resources 에 번들되어 클래스패스에 올라가야 한다)");
        }
        long count = 0;
        // 자기가 소유한 두 컬럼만 덮어쓴다 — V4 파생 6컬럼(air_density, norm_wind_speed, is_stopped,
        // train_mask, expected_power_pooled, expected_power_unit)은 건드리지 않는다.
        // 새 CSV 의 키집합이 옛 것의 초집합이라 삭제 없이 upsert 만으로 수렴한다.
        String sql = "INSERT INTO scada_record (turbine_id, recorded_at, power_output, wind_speed)"
                + " VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE"
                + " power_output = VALUES(power_output), wind_speed = VALUES(wind_speed)";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String line;
            int batch = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int c1 = line.indexOf(',');
                int c2 = line.indexOf(',', c1 + 1);
                int c3 = line.indexOf(',', c2 + 1);
                if (c1 < 0 || c2 < 0 || c3 < 0) {
                    // 3열 CSV 를 관용하면 안 된다. 옛 아티팩트가 클래스패스에 올라오면 wind_speed 를
                    // 전부 NULL 로 넣고 '성공'으로 끝나, 되돌릴 수 없는 마이그레이션이 조용히 오적재된다.
                    throw new IllegalStateException(
                            "SCADA CSV 는 4열이어야 한다(turbine_id,recorded_at,power_output,wind_speed). "
                                    + (count + 1) + "번째 줄: " + line);
                }
                ps.setLong(1, Long.parseLong(line.substring(0, c1)));
                ps.setString(2, line.substring(c1 + 1, c2));
                setNullable(ps, 3, line.substring(c2 + 1, c3));
                setNullable(ps, 4, line.substring(c3 + 1));
                ps.addBatch();
                count++;
                if (++batch == BATCH_SIZE) {
                    ps.executeBatch();
                    batch = 0;
                }
                if (count % 100_000 == 0) {
                    System.out.printf("[ScadaSeed] loadCsv 진행 %,d행%n", count);
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
        return count;
    }

    private static void setNullable(PreparedStatement ps, int index, String raw) throws Exception {
        if (raw.isEmpty()) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, Double.parseDouble(raw));
        }
    }

    /**
     * 2025 구간을 1년 밀어 2026 을 만든다(V12 가 하던 일).
     * <p>
     * 2025·2026 모두 평년이라 {@code + INTERVAL 1 YEAR} 가 1:1 로 대응한다(2/29 없음).
     * <b>wind_speed 를 함께 복사한다</b> — V12 원본 SQL 은 power_output 만 옮겨서 2026(데모 기준 연도)에
     * 풍속이 비었고, 그러면 그 구간에서 기대발전량 예측이 성립하지 않는다.
     */
    static long copy2025To2026(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    INSERT INTO scada_record (turbine_id, recorded_at, power_output, wind_speed)
                    SELECT turbine_id, recorded_at + INTERVAL 1 YEAR, power_output, wind_speed
                    FROM scada_record
                    WHERE recorded_at >= '2025-01-01 00:00:00' AND recorded_at < '2026-01-01 00:00:00'
                    ON DUPLICATE KEY UPDATE
                        power_output = VALUES(power_output), wind_speed = VALUES(wind_speed)
                    """);
        }
    }

    /**
     * 화순 U1~U7(7~13) 의 2026 구간을 보성 B1~B7(15~21) 로 복사한다(V13 이 하던 일).
     * <p>
     * turbine_id 오프셋 +8 은 V13 의 CASE 매핑(7→15 … 13→21)과 같다. 여기서도
     * <b>wind_speed 를 함께 복사한다</b>(V13 원본 SQL 은 빠뜨렸다).
     * 선행 조건: {@link #copy2025To2026} 이 2026 구간을 만들어 둔 뒤여야 하고,
     * turbine 15~21 행이 존재해야 한다(V13 이 마스터로 넣는다 — FK).
     */
    static long deriveBoseong(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    INSERT INTO scada_record (turbine_id, recorded_at, power_output, wind_speed)
                    SELECT turbine_id + 8, recorded_at, power_output, wind_speed
                    FROM scada_record
                    WHERE turbine_id BETWEEN 7 AND 13
                      AND recorded_at >= '2026-01-01 00:00:00' AND recorded_at < '2027-01-01 00:00:00'
                    ON DUPLICATE KEY UPDATE
                        power_output = VALUES(power_output), wind_speed = VALUES(wind_speed)
                    """);
        }
    }

    /**
     * 일별 발전량 = 시간별 power_output 합(에너지). 결측(NULL)은 SUM 이 무시하고, 전부 결측인 날은 NULL.
     * <p>
     * SELECT 와 GROUP BY 에 <b>동일한 표현식</b>을 쓴다 — only_full_group_by(MySQL8 기본)는 GROUP BY 키와
     * 다른 표현식을 비집계 SELECT 에 두면 거부한다. DATE 값을 DATETIME(6) 컬럼에 넣으면 자정으로 확장된다.
     */
    static long rollupDaily(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    INSERT INTO daily_generation (turbine_id, stat_at, daily_power_output)
                    SELECT turbine_id, DATE(recorded_at), SUM(power_output)
                    FROM scada_record
                    GROUP BY turbine_id, DATE(recorded_at)
                    """);
        }
    }

    /** 월별 발전량 = 시간별 power_output 합(에너지). stat_at 은 해당 월 1일 00:00(KST). */
    static long rollupMonthly(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    INSERT INTO monthly_generation (turbine_id, stat_at, monthly_power_output)
                    SELECT turbine_id,
                           STR_TO_DATE(DATE_FORMAT(recorded_at, '%Y-%m-01'), '%Y-%m-%d'),
                           SUM(power_output)
                    FROM scada_record
                    GROUP BY turbine_id, STR_TO_DATE(DATE_FORMAT(recorded_at, '%Y-%m-01'), '%Y-%m-%d')
                    """);
        }
    }
}
