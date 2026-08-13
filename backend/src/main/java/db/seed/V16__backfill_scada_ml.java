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
 * (pooled/터빈별)·플래그를 계산한 결과를 번들 CSV 로 담았고, 여기서 적재한다.
 * <p>
 * <b>왜 임시 테이블 + UPDATE JOIN 인가</b> — 첫 구현은 행마다 UPDATE 를 배치로 보냈는데, UPDATE 는
 * {@code rewriteBatchedStatements} 의 다중행 재작성 혜택을 받지 못해 91.7만 왕복 ≈ 14분이 걸렸다.
 * ECS 헬스체크 유예(~9분)가 그보다 짧아 기동 중 태스크가 반복 킬되는 크래시 루프가 됐다(실측 2회,
 * 매번 50만 행 부근에서 킬·롤백). 임시 테이블로 스테이징(INSERT 는 다중행 재작성으로 초고속)한 뒤
 * 서버 안에서 UPDATE JOIN 한 방으로 반영하면 전체가 2~3분으로 줄어 유예 안에 끝난다.
 * {@code CREATE TEMPORARY TABLE} 은 암묵 커밋을 일으키지 않으므로 실패 시 DML 전체 롤백도 유지된다.
 * <p>
 * 결측 규약: 기상·풍속이 없던 시각은 expected 가 NULL(예측 불가 신호)이다 — 0 으로 채우지 않는다.
 * 2026 은 2025 결과를 1년 밀어 복사하고, 보성(15~21)은 화순(7~13) 2026 을 복사한다
 * ({@link ScadaSeed#deriveBoseong} 의 +8 매핑). raw(power_output·wind_speed)는 손대지 않는다.
 */
public class V16__backfill_scada_ml extends BaseJavaMigration {

    static final String RESOURCE = "db/seed/ml_backfill.csv.gz";
    private static final int BATCH_SIZE = 5_000;

    /** 번들 CSV 의 행 수 계약(V15 와 동일 논리 — CSV·마이그레이션은 같은 커밋의 불변 쌍). */
    static final long EXPECTED_SOURCE_ROWS = 917_712;

    private static final String STAGE = "ml_backfill_stage";

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        try (Statement st = conn.createStatement()) {
            // 세션 종료 시 자동 소멸. PK 를 scada_record 와 동일하게 잡아 JOIN 이 PK 룩업이 되게 한다.
            st.executeUpdate("CREATE TEMPORARY TABLE " + STAGE + " ("
                    + " turbine_id BIGINT NOT NULL,"
                    + " recorded_at DATETIME(6) NOT NULL,"
                    + " air_density DOUBLE NULL,"
                    + " norm_wind_speed DOUBLE NULL,"
                    + " is_stopped TINYINT NULL,"
                    + " train_mask TINYINT NULL,"
                    + " expected_power_pooled DOUBLE NULL,"
                    + " expected_power_unit DOUBLE NULL,"
                    + " PRIMARY KEY (turbine_id, recorded_at)) ENGINE = InnoDB");
        }

        long staged = stageCsv(conn);
        if (staged != EXPECTED_SOURCE_ROWS) {
            throw new IllegalStateException("ML 백필 CSV 행 수 불일치: expected=" + EXPECTED_SOURCE_ROWS
                    + ", actual=" + staged + " — 리소스가 불완전하다");
        }

        // 키 정합: 스테이지의 모든 (turbine_id, recorded_at) 가 scada_record 에 실재해야 한다.
        // 하나라도 빠지면 raw 시드(V11/V14)와 CSV 버전이 어긋난 것 — 부분 반영 대신 중단(DML 롤백).
        long matched;
        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM " + STAGE + " s"
                    + " JOIN scada_record r ON r.turbine_id = s.turbine_id AND r.recorded_at = s.recorded_at");
            rs.next();
            matched = rs.getLong(1);
        }
        if (matched != staged) {
            throw new IllegalStateException("ML 백필 키 불일치: CSV " + staged + "행 중 "
                    + matched + "행만 scada_record 에 존재 — raw 시드(V11/V14)와 CSV 버전이 어긋났다");
        }

        long applied;
        try (Statement st = conn.createStatement()) {
            applied = st.executeUpdate("""
                    UPDATE scada_record r
                    JOIN %s s ON s.turbine_id = r.turbine_id AND s.recorded_at = r.recorded_at
                    SET r.air_density = s.air_density,
                        r.norm_wind_speed = s.norm_wind_speed,
                        r.is_stopped = s.is_stopped,
                        r.train_mask = s.train_mask,
                        r.expected_power_pooled = s.expected_power_pooled,
                        r.expected_power_unit = s.expected_power_unit
                    """.formatted(STAGE));
        }

        long y2026 = copy2025To2026(conn);
        long boseong = deriveBoseong(conn);
        System.out.printf("[V16] 스테이징 %,d행(키 매칭 %,d) → 반영 %,d행 + 2026 복사 %,d행 + 보성 복사 %,d행%n",
                staged, matched, applied, y2026, boseong);

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TEMPORARY TABLE " + STAGE);
            var rs = st.executeQuery(
                    "SELECT COUNT(*), COUNT(expected_power_unit) FROM scada_record");
            rs.next();
            System.out.printf("[V16] 사후검증: scada %,d행 중 expected_unit 보유 %,d행%n",
                    rs.getLong(1), rs.getLong(2));
        }
    }

    /**
     * 번들 CSV → 임시 테이블 INSERT.
     * <p>
     * CSV: {@code turbine_id,recorded_at,air_density,norm_wind_speed,is_stopped,train_mask,
     * expected_power_pooled,expected_power_unit} (헤더 없음). 8열이 아니면 즉시 중단.
     * INSERT 배치는 {@code rewriteBatchedStatements=true}(운영 JDBC URL)에서 다중행 문장으로
     * 재작성되어 UPDATE 배치와 달리 왕복이 상수화된다.
     */
    static long stageCsv(Connection conn) throws Exception {
        InputStream in = V16__backfill_scada_ml.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + RESOURCE);
        }
        String sql = "INSERT INTO " + STAGE
                + " (turbine_id, recorded_at, air_density, norm_wind_speed, is_stopped, train_mask,"
                + " expected_power_pooled, expected_power_unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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
                ps.setLong(1, Long.parseLong(cols[0]));
                ps.setString(2, cols[1]);
                setNullableDouble(ps, 3, cols[2]);
                setNullableDouble(ps, 4, cols[3]);
                setNullableInt(ps, 5, cols[4]);
                setNullableInt(ps, 6, cols[5]);
                setNullableDouble(ps, 7, cols[6]);
                setNullableDouble(ps, 8, cols[7]);
                ps.addBatch();
                count++;
                if (++batch == BATCH_SIZE) {
                    ps.executeBatch();
                    batch = 0;
                }
                if (count % 100_000 == 0) {
                    System.out.printf("[V16] 스테이징 진행 %,d행%n", count);
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
