package db.seed;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;

/**
 * 과거 SCADA 발전 이력 시드(운영·로컬 전용, test 프로파일에서는 이 location 을 제외해 실행되지 않는다).
 * <p>
 * 이 마이그레이션은 {@code classpath:db/seed} location 에 있고, {@code application-test.yaml} 은
 * {@code spring.flyway.locations} 를 {@code classpath:db/migration} 로만 좁힌다. 따라서 CI/통합테스트
 * (Testcontainers)에서는 <b>이 91만 행 적재가 돌지 않는다</b> — 테스트는 마스터(V10)까지만 본다.
 * <p>
 * 데이터는 화순/장흥 SCADA 원본 CSV(EUC-KR)를 시간 단위로 정제한 것이다(장흥 2022~2024 10분 → 시간 평균,
 * 화순·장흥 2025 시간별 그대로). 원본을 그대로 SQL 로 풀면 수십 MB 라, gzip CSV 리소스를
 * 번들해 여기서 스트리밍 적재한다. private RDS 라 외부 로더가 닿지 못하므로 앱이 적재하는 이 경로가 유일하다.
 * <p>
 * 리소스는 {@code db/seed/build_scada_hourly.py} 가 원본 2종에서 생성한다. 이전 통합본에는 화순 발전량
 * 80,324건(전체의 8.81%, 그것도 1000kW 이상 고출력 구간만)이 빈 값으로 들어가 있었다 — 화순 원본이
 * 그 값들을 {@code "1,291.20"} 처럼 천단위 쉼표로 적는데 변환기가 파싱에 실패한 탓이다. 재생성본은
 * 그 값을 모두 살렸고, 함께 버려졌던 <b>나셀 풍속</b>도 4번째 열로 되살렸다.
 * <p>
 * 결측 처리: 원본에 <b>행 자체가 없던 시각</b>도 호기별 [최초, 최종] 구간 안이면 값이 NULL 인 행으로
 * 채워져 있다(5,798행). 조회 API 가 빈 시각을 배열에서 누락시키지 않게 하기 위해서다. 따라서 적재 후
 * scada_record 에는 그 구간에 빠진 정시가 없고, '데이터 없음'은 행의 부재가 아니라 NULL 로 표현된다.
 * <p>
 * 일/월 발전량은 적재한 raw 에서 SQL 로 합산 유도한다(SUM = 에너지). KST 기준 일/월 키라
 * {@code PowerQueryService}(Asia/Seoul)의 조회 키와 정렬된다.
 */
public class V11__seed_scada_history extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // 재적재 안전장치(부분 실패 후 repair→재실행 대비). 신규 적용 시 V10 이 이미 비워 둬 no-op 이다.
        ScadaSeed.deleteAll(conn);

        long inserted = ScadaSeed.loadCsv(conn);
        long daily = ScadaSeed.rollupDaily(conn);
        long monthly = ScadaSeed.rollupMonthly(conn);

        System.out.printf("[V11] SCADA 시드 완료: scada_record=%d, daily_generation=%d, monthly_generation=%d%n",
                inserted, daily, monthly);
    }
}
