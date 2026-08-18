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
 * AWS 기상 관측 이력 시드(운영·로컬 전용, test 프로파일에서는 이 location 을 제외해 실행되지 않는다).
 * <p>
 * 기대발전량 예측(밀도보정풍속)이 관측소의 기온·기압·습도를 요구하는데 {@code aws_record} 가 비어 있었다.
 * 기상자료개방포털의 AWS 시간자료(화순 741: 2015~2025, 장흥 778: 2021~2025 — 각 단지 SCADA 구간을 덮는다)를
 * {@code db/seed/build_aws_hourly.py} 로 정제해 번들했고, 여기서 스트리밍 적재한다.
 * private RDS 라 외부 로더가 닿지 못하므로 앱이 적재하는 이 경로가 유일하다(V11 과 동일).
 * <p>
 * 기압은 <b>현지기압</b>이다(해면기압 아님) — 밀도의 고도 보정(측고식)이 관측지점 기압에서 출발하므로
 * 해면 환원값을 쓰면 이중 보정이 된다. 빈 값은 NULL(관측 없음 ≠ 0) 로 넣는다 — 기대발전량 계산이
 * 이 NULL 을 "예측 불가"로 전파한다.
 * <p>
 * 2026 은 2025 를 1년 밀어 복사한다(SCADA 의 {@code copy2025To2026} 과 같은 이유 — 데모 기준 연도에
 * 관측이 항상 존재해야 하고, 평년끼리라 1:1 대응이다). KMA 실시간 수집 cron 이 붙기 전까지의 기반이다.
 */
public class V15__seed_aws_history extends BaseJavaMigration {

    static final String RESOURCE = "db/seed/aws_hourly.csv.gz";
    private static final int BATCH_SIZE = 5_000;

    /**
     * 번들 CSV 의 행 수 계약. CSV 와 이 마이그레이션은 같은 커밋에 얼어붙는 불변 쌍이므로
     * 하드코딩이 맞다 — 리소스가 잘리거나 다른 버전이 클래스패스에 오르면 여기서 즉시 중단된다.
     */
    static final long EXPECTED_SOURCE_ROWS = 140_122;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        long inserted = loadCsv(conn);
        if (inserted != EXPECTED_SOURCE_ROWS) {
            throw new IllegalStateException("AWS 시드 행 수 불일치: expected=" + EXPECTED_SOURCE_ROWS
                    + ", actual=" + inserted + " — 리소스가 불완전하다(부분 적재는 DML 이라 롤백된다)");
        }
        long copied = copy2025To2026(conn);
        System.out.printf("[V15] aws_record 적재 %,d행 + 2026 복사 %,d행%n", inserted, copied);

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*), COUNT(pressure) FROM aws_record");
            rs.next();
            System.out.printf("[V15] 사후검증: 총 %,d행, 기압 보유 %,d행%n", rs.getLong(1), rs.getLong(2));
        }
    }

    /**
     * 번들된 gzip CSV → aws_record.
     * <p>
     * CSV: {@code aws_station_id,recorded_at,temperature,pressure,humidity,wind_direction,precipitation}
     * (헤더 없음, 임베디드 콤마 없음). 7열이 아니면 즉시 중단한다 — 열 수 관용은 조용한 오적재가 된다.
     * 재실행 안전: PK(관측소,시각) upsert 로 수렴한다.
     */
    static long loadCsv(Connection conn) throws Exception {
        InputStream in = V15__seed_aws_history.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + RESOURCE);
        }
        String sql = "INSERT INTO aws_record"
                + " (aws_station_id, recorded_at, temperature, pressure, humidity, wind_direction, precipitation)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE"
                + " temperature = VALUES(temperature), pressure = VALUES(pressure),"
                + " humidity = VALUES(humidity), wind_direction = VALUES(wind_direction),"
                + " precipitation = VALUES(precipitation)";
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
                if (cols.length != 7) {
                    throw new IllegalStateException("AWS CSV 는 7열이어야 한다. "
                            + (count + 1) + "번째 줄: " + line);
                }
                ps.setLong(1, Long.parseLong(cols[0]));
                ps.setString(2, cols[1]);
                for (int i = 2; i < 7; i++) {
                    if (cols[i].isEmpty()) {
                        ps.setNull(i + 1, Types.DOUBLE);
                    } else {
                        ps.setDouble(i + 1, Double.parseDouble(cols[i]));
                    }
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

    /** 2025 관측을 1년 밀어 2026 을 만든다(평년끼리 1:1). 재실행 안전: upsert 수렴. */
    static long copy2025To2026(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("""
                    INSERT INTO aws_record
                        (aws_station_id, recorded_at, temperature, pressure, humidity, wind_direction, precipitation)
                    SELECT aws_station_id, recorded_at + INTERVAL 1 YEAR,
                           temperature, pressure, humidity, wind_direction, precipitation
                    FROM aws_record
                    WHERE recorded_at >= '2025-01-01 00:00:00' AND recorded_at < '2026-01-01 00:00:00'
                    ON DUPLICATE KEY UPDATE
                        temperature = VALUES(temperature), pressure = VALUES(pressure),
                        humidity = VALUES(humidity), wind_direction = VALUES(wind_direction),
                        precipitation = VALUES(precipitation)
                    """);
        }
    }
}
