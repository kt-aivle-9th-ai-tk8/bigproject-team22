package db.seed;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * SCADA 재적재 패치(운영·로컬 전용, test 프로파일에서는 이 location 을 제외해 실행되지 않는다).
 *
 * <h2>왜 필요한가</h2>
 * V11 이 쓰던 통합 CSV 에 결함이 있어 리소스를 교체했는데, <b>Flyway 는 이미 적용된 V11 을 다시 돌리지
 * 않는다.</b> 따라서 V11 이 적용된 DB(우리의 유일한 DB)는 옛 데이터를 그대로 들고 있다. DB 초기화가
 * 불가능하므로 같은 결과로 수렴시키는 패치가 필요하다.
 *
 * <p>교체된 CSV 가 고친 것:
 * <ul>
 *   <li><b>화순 발전량 80,324건(8.81%) 복구</b> — 화순 원본은 {@code |값| >= 1000} 을 예외 없이
 *       {@code "1,291.20"} 처럼 천단위 쉼표로 적는데, 이전 변환기가 파싱에 실패해 전부 빈 값으로
 *       버렸다. 하필 고출력 구간만 통째로 사라져 파워커브 상단이 잘려 있었다.</li>
 *   <li><b>나셀 풍속 복원</b> — 원본에 있는데 이전 통합본이 3열만 남기고 버렸다.
 *       기대발전량 예측(serving/ml/predict.py: norm_wind_speed)이 필수로 요구한다.</li>
 *   <li><b>결측 시각 5,798개를 값 NULL 행으로 채움</b> — 적재 계약이 "값이 없어도 행은 만든다"
 *       이므로 시드도 그 계약을 만족한 상태여야 한다.</li>
 * </ul>
 *
 * <h2>raw 를 지우지 않는다 — V4 파생 6컬럼 보존</h2>
 * {@code V4__anomaly_detection_schema.sql} 이 scada_record 에 붙인 {@code air_density},
 * {@code norm_wind_speed}, {@code is_stopped}, {@code train_mask}, {@code expected_power_pooled},
 * {@code expected_power_unit} 은 추론 산출물이고 이 리포지토리에 재생성 경로가 없다. 소비자는 실재한다
 * (report-agent 의 datasource, anomaly 서비스의 계층 A/B 판정). 그래서 {@code DELETE} 대신
 * {@code ON DUPLICATE KEY UPDATE} 로 <b>power_output·wind_speed 두 컬럼만</b> 덮어쓴다.
 * 새 CSV 의 키집합이 옛 CSV 의 초집합임을 실측으로 확인했고, 잔여 행이 생기면 아래 사후검증이 잡는다.
 *
 * <h2>왜 V12·V13 SQL 을 그대로 재실행하지 않는가</h2>
 * 둘 다 {@code wind_speed} 를 복사하지 않는다. 원본을 그대로 다시 돌리면 2026(데모 기준 연도)과
 * 보성 전체에 풍속이 비어, 정작 풍속을 복원한 의미가 사라진다. 그래서 {@link ScadaSeed} 의
 * 메서드로 같은 파생을 다시 만들되 풍속을 함께 싣는다. V13 이 넣은 마스터 데이터
 * (wind_farm 3·4 / turbine 15~21)는 건드리지 않으며, 보성 복사의 FK 를 위해 V13 뒤에 와야 한다.
 *
 * <h2>anomaly_event 는 의도적으로 손대지 않는다</h2>
 * 옛 raw 에서 산출된 이벤트가 남는다. 지우지 않는 이유는 {@code report.anomaly_event_id} 가
 * {@code ON DELETE SET NULL} 로 이 테이블을 참조하기 때문이다({@code V8__report_adjustments.sql}) —
 * 인용된 이벤트를 지우면 보고서 링크가 끊기고 되돌릴 수 없다. 정리 여부는 사람이 판단한다.
 *
 * <h2>실패했을 때 — "백업을 복원하라"는 로그를 따르지 말 것</h2>
 * 순수 DML 이고 기본 트랜잭션 안에서 돌므로 <b>데이터는 전부 롤백된다.</b> 다만 MySQL 은 DDL 트랜잭션을
 * 지원하지 않아 {@code flyway_schema_history} 에 {@code success=0} 행이 남고, 이후 기동이
 * "Detected failed migration to version 14" 로 거부된다 → 재적용 전에 {@code flyway repair} 가 필요하다.
 * 이때 Flyway 가 찍는 {@code "Please restore backups and roll back database and code!"} 는 그 상황에서
 * 항상 뜨는 정형 문구이며 <b>따르지 말 것</b> — 롤백은 이미 끝났고, 유일 DB 를 스냅샷으로 되돌리면
 * 그때부터가 진짜 비가역 유실이다.
 *
 * <h2>재실행 안전</h2>
 * upsert + 결정적 파생이라 몇 번을 돌려도 같은 상태가 된다. 신규 DB 에서 V11→V12→V13→V14 로 흘러도
 * 결과는 동일하다.
 */
public class V14__reload_scada_with_fixed_source extends BaseJavaMigration {

    /** CSV 917,712 + 2026 복사 122,640 + 보성 61,320. 생성기 산출물에서 직접 계산한 값이다. */
    private static final long EXPECTED_SCADA_ROWS = 1_101_672L;
    private static final long EXPECTED_TURBINES = 21L;
    /** 격자 결측 5,798 + 그중 2025 구간이 2026 으로 복사되며 늘어난 103. */
    private static final long EXPECTED_NULL_ROWS = 5_901L;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        ScadaSeed.deleteDerivedAggregates(conn); // raw 는 건드리지 않는다

        long raw = ScadaSeed.loadCsv(conn);           // 장흥 1~6 · 화순 7~14 (2015~2025)
        long y2026 = ScadaSeed.copy2025To2026(conn);  // 2025 → 2026 (V12 가 하던 일 + 풍속)
        long boseong = ScadaSeed.deriveBoseong(conn); // 화순 2026 → 보성 15~21 (V13 이 하던 일 + 풍속)
        long daily = ScadaSeed.rollupDaily(conn);
        long monthly = ScadaSeed.rollupMonthly(conn);

        verify(conn); // 어긋나면 예외 → 순수 DML 이라 전체 롤백된다

        System.out.printf("[V14] SCADA 재적재 완료: csv=%d, 2026=%d, 보성=%d"
                        + " → daily_generation=%d, monthly_generation=%d%n",
                raw, y2026, boseong, daily, monthly);
    }

    /**
     * 로그의 계산값이 아니라 <b>DB 실측</b>으로 사후조건을 건다.
     * 잘못된 CSV 아티팩트가 클래스패스에 올라오거나 예상 밖 잔여 행이 있으면 여기서 걸린다.
     */
    private static void verify(Connection conn) throws Exception {
        check(conn, "SELECT COUNT(*) FROM scada_record",
                EXPECTED_SCADA_ROWS, "scada_record 총 행수");
        check(conn, "SELECT COUNT(DISTINCT turbine_id) FROM scada_record",
                EXPECTED_TURBINES, "적재 호기 수");
        check(conn, "SELECT COUNT(*) FROM scada_record WHERE power_output IS NULL",
                EXPECTED_NULL_ROWS, "결측(NULL) power_output 행수");
        check(conn, "SELECT COUNT(*) FROM scada_record WHERE wind_speed IS NULL",
                EXPECTED_NULL_ROWS, "결측(NULL) wind_speed 행수");
        // 적재 계약: 호기별 [최초, 최종] 구간에 빠진 정시가 없어야 한다.
        check(conn, """
                SELECT COUNT(*) FROM (
                  SELECT turbine_id
                  FROM scada_record
                  GROUP BY turbine_id
                  HAVING COUNT(*) <> TIMESTAMPDIFF(HOUR, MIN(recorded_at), MAX(recorded_at)) + 1
                ) t
                """, 0L, "정시 격자가 불완전한 호기 수");
    }

    private static void check(Connection conn, String sql, long expected, String what) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            long actual = rs.getLong(1);
            if (actual != expected) {
                throw new IllegalStateException("[V14] 사후검증 실패 — " + what
                        + ": 기대 " + expected + ", 실제 " + actual
                        + ". 잘못된 CSV 아티팩트이거나 예상 밖의 잔여 행이 있다. 전체 롤백한다.");
            }
        }
    }
}
