package db.seed;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

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
 * SCADA ML 파생 7컬럼 백필(운영·로컬 전용, test 프로파일에서는 이 location 을 제외해 실행되지 않는다).
 * <p>
 * V4 가 붙인 {@code air_density}/{@code norm_wind_speed}/{@code is_stopped}/{@code train_mask}/
 * {@code expected_power_pooled}/{@code expected_power_unit} 는 지금까지 전부 NULL 이었고, 그래서
 * 운영·단지 보고서가 유효 행 0(터빈 0건)으로 나왔다. {@code db/seed/build_ml_backfill.py} 가
 * AWS 관측(V15)과 SCADA 풍속을 병합해 밀도보정풍속(IEC 61400-12-1)·LightGBM 기대발전량
 * (pooled/터빈별)·플래그를 계산한 결과를 번들 CSV 로 담았고, 여기서 스트리밍 UPDATE 한다.
 * <p>
 * 결측 규약: 기상·풍속이 없던 시각은 expected 가 NULL(예측 불가 신호)이다 — 0 으로 채우지 않는다.
 * 소비자(report-agent)는 expected NULL 행을 유효 모집단에서 제외한다.
 * <p>
 * 2026 은 2025 의 계산 결과를 1년 밀어 복사하고(원본 관측이 2025 까지 — SCADA V12/V14 와 동일한 이유),
 * 보성(15~21)은 화순(7~13) 2026 을 복사한다({@link ScadaSeed#deriveBoseong} 과 같은 +8 매핑).
 * raw(power_output·wind_speed)는 손대지 않는다 — 이 마이그레이션은 파생 컬럼만 소유한다.
 */
public class V16__backfill_scada_ml extends BaseJavaMigration {

    static final String RESOURCE = "db/seed/ml_backfill.csv.gz";
    private static final int BATCH_SIZE = 5_000;

    private static final String ML_SET = "air_density = ?, norm_wind_speed = ?, is_stopped = ?,"
            + " train_mask = ?, expected_power_pooled = ?, expected_power_unit = ?";

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        long updated = applyCsv(conn);
        long y2026 = copy2025To2026(conn);
        long boseong = deriveBoseong(conn);
        System.out.printf("[V16] ML 백필 %,d행 + 2026 복사 %,d행 + 보성 복사 %,d행%n",
                updated, y2026, boseong);

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT COUNT(*), COUNT(expected_power_unit) FROM scada_record");
            rs.next();
            System.out.printf("[V16] 사후검증: scada %,d행 중 expected_unit 보유 %,d행%n",
                    rs.getLong(1), rs.getLong(2));
        }
    }

    /**
     * 번들 CSV → PK(turbine_id, recorded_at) 기준 UPDATE.
     * <p>
     * CSV: {@code turbine_id,recorded_at,air_density,norm_wind_speed,is_stopped,train_mask,
     * expected_power_pooled,expected_power_unit} (헤더 없음). 8열이 아니면 즉시 중단.
     * INSERT 가 아니라 UPDATE 다 — 행이 없는 키는 raw 시드(V11/V14)와 어긋난 것이므로
     * 조용히 만들어내지 않고 개수 불일치로 드러나게 둔다(사후검증 로그).
     */
    static long applyCsv(Connection conn) throws Exception {
        InputStream in = V16__backfill_scada_ml.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + RESOURCE);
        }
        String sql = "UPDATE scada_record SET " + ML_SET
                + " WHERE turbine_id = ? AND recorded_at = ?";
        long count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String line;
            int batch = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length != 8) {
                    throw new IllegalStateException("ML 백필 CSV 는 8열이어야 한다. "
                            + (count + 1) + "번째 줄: " + line);
                }
                setNullableDouble(ps, 1, cols[2]);           // air_density
                setNullableDouble(ps, 2, cols[3]);           // norm_wind_speed
                setNullableInt(ps, 3, cols[4]);              // is_stopped
                setNullableInt(ps, 4, cols[5]);              // train_mask
                setNullableDouble(ps, 5, cols[6]);           // expected_power_pooled
                setNullableDouble(ps, 6, cols[7]);           // expected_power_unit
                ps.setLong(7, Long.parseLong(cols[0]));
                ps.setString(8, cols[1]);
                ps.addBatch();
                count++;
                if (++batch == BATCH_SIZE) {
                    ps.executeBatch();
                    batch = 0;
                }
                if (count % 100_000 == 0) {
                    System.out.printf("[V16] 진행 %,d행%n", count);
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
        return count;
    }

    /** 2025 파생값을 1년 밀어 2026 행에 복사한다(평년끼리 1:1 — SCADA/AWS 복사와 동일 근거). */
    static long copy2025To2026(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    UPDATE scada_record y26
                    JOIN scada_record y25
                      ON y25.turbine_id = y26.turbine_id
                     AND y25.recorded_at = y26.recorded_at - INTERVAL 1 YEAR
                    SET y26.air_density = y25.air_density,
                        y26.norm_wind_speed = y25.norm_wind_speed,
                        y26.is_stopped = y25.is_stopped,
                        y26.train_mask = y25.train_mask,
                        y26.expected_power_pooled = y25.expected_power_pooled,
                        y26.expected_power_unit = y25.expected_power_unit
                    WHERE y26.recorded_at >= '2026-01-01 00:00:00'
                      AND y26.recorded_at < '2027-01-01 00:00:00'
                    """);
        }
    }

    /** 보성(15~21) 2026 파생값 = 화순(7~13) 2026 복사({@code deriveBoseong} 의 +8 매핑 그대로). */
    static long deriveBoseong(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    UPDATE scada_record b
                    JOIN scada_record h
                      ON h.turbine_id = b.turbine_id - 8
                     AND h.recorded_at = b.recorded_at
                    SET b.air_density = h.air_density,
                        b.norm_wind_speed = h.norm_wind_speed,
                        b.is_stopped = h.is_stopped,
                        b.train_mask = h.train_mask,
                        b.expected_power_pooled = h.expected_power_pooled,
                        b.expected_power_unit = h.expected_power_unit
                    WHERE b.turbine_id BETWEEN 15 AND 21
                      AND b.recorded_at >= '2026-01-01 00:00:00'
                      AND b.recorded_at < '2027-01-01 00:00:00'
                    """);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, String raw) throws Exception {
        if (raw.isEmpty()) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, Double.parseDouble(raw));
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, String raw) throws Exception {
        if (raw.isEmpty()) {
            ps.setNull(index, Types.TINYINT);
        } else {
            ps.setInt(index, Integer.parseInt(raw));
        }
    }
}
