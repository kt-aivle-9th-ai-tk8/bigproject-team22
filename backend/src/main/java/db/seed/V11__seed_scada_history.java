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
 * 과거 SCADA 발전 이력 시드(운영·로컬 전용, test 프로파일에서는 이 location 을 제외해 실행되지 않는다).
 * <p>
 * 이 마이그레이션은 {@code classpath:db/seed} location 에 있고, {@code application-test.yaml} 은
 * {@code spring.flyway.locations} 를 {@code classpath:db/migration} 로만 좁힌다. 따라서 CI/통합테스트
 * (Testcontainers)에서는 <b>이 91만 행 적재가 돌지 않는다</b> — 테스트는 마스터(V10)까지만 본다.
 * <p>
 * 데이터는 화순/장흥 SCADA 원본 CSV(EUC-KR)를 시간 단위로 정제한 것이다(장흥 2022~2024 10분 → 시간 평균,
 * 화순·장흥 2025 시간별 그대로, 결측은 NULL 유지). 원본을 그대로 SQL 로 풀면 수십 MB 라, gzip CSV 리소스를
 * 번들해 여기서 스트리밍 적재한다. private RDS 라 외부 로더가 닿지 못하므로 앱이 적재하는 이 경로가 유일하다.
 * <p>
 * 일/월 발전량은 적재한 raw 에서 SQL 로 합산 유도한다(SUM = 에너지). KST 기준 일/월 키라
 * {@code PowerQueryService}(Asia/Seoul)의 조회 키와 정렬된다.
 */
public class V11__seed_scada_history extends BaseJavaMigration {

    private static final String RESOURCE = "db/seed/scada_hourly.csv.gz";
    private static final int BATCH_SIZE = 5_000;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // 재적재 안전장치(부분 실패 후 repair→재실행 대비). 신규 적용 시 V10 이 이미 비워 둬 no-op 이다.
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM monthly_generation");
            st.executeUpdate("DELETE FROM daily_generation");
            st.executeUpdate("DELETE FROM scada_record");
        }

        long inserted = loadScada(conn);
        long daily = rollupDaily(conn);
        long monthly = rollupMonthly(conn);

        System.out.printf("[V11] SCADA 시드 완료: scada_record=%d, daily_generation=%d, monthly_generation=%d%n",
                inserted, daily, monthly);
    }

    private long loadScada(Connection conn) throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + RESOURCE
                    + " (src/main/resources 에 번들되어 클래스패스에 올라가야 한다)");
        }
        long count = 0;
        String sql = "INSERT INTO scada_record (turbine_id, recorded_at, power_output) VALUES (?, ?, ?)";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String line;
            int batch = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                // CSV: turbine_id,recorded_at,power_output  (임베디드 콤마 없음이 보장됨)
                int c1 = line.indexOf(',');
                int c2 = line.indexOf(',', c1 + 1);
                ps.setLong(1, Long.parseLong(line.substring(0, c1)));
                ps.setString(2, line.substring(c1 + 1, c2));
                String power = line.substring(c2 + 1);
                if (power.isEmpty()) {
                    ps.setNull(3, Types.DOUBLE); // 결측: 값 0 이 아니라 '없음'으로 보존
                } else {
                    ps.setDouble(3, Double.parseDouble(power));
                }
                ps.addBatch();
                count++;
                if (++batch == BATCH_SIZE) {
                    ps.executeBatch();
                    batch = 0;
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
        return count;
    }

    /**
     * 일별 발전량 = 시간별 power_output 합(에너지). 결측(NULL)은 SUM 이 무시하고, 전부 결측인 날은 NULL.
     * <p>
     * SELECT 와 GROUP BY 에 <b>동일한 표현식</b>을 쓴다 — only_full_group_by(MySQL8 기본)는 GROUP BY 키와
     * 다른 표현식을 비집계 SELECT 에 두면 거부한다. DATE 값을 DATETIME(6) 컬럼에 넣으면 자정으로 확장된다.
     */
    private long rollupDaily(Connection conn) throws Exception {
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
    private long rollupMonthly(Connection conn) throws Exception {
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
