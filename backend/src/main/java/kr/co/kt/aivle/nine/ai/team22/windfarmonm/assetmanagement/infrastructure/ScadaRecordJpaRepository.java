package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 발전량 원천(scada_record) 조회. {@code ScadaRecordRepositoryImpl} 어댑터에서만 사용한다.
 */
public interface ScadaRecordJpaRepository extends JpaRepository<ScadaRecord, TurbineInstantId> {

    Optional<ScadaRecord> findTopByTurbineIdOrderByTimeDesc(Long turbineId);

    /**
     * 터빈별 최신 1건.
     * <p>
     * TODO(성능): 이 쿼리는 <b>대상 터빈들의 전 이력 행을 훑는다</b>. 상관 서브쿼리 자체는 복합 PK
     *  (turbine_id, recorded_at) 덕에 싸지만, 바깥 쿼리가 IN 목록 터빈의 모든 행을 읽으면서 행마다
     *  이를 평가하기 때문이다. 즉 비용이 <b>누적 이력에 비례</b>해 시간이 갈수록 악화된다
     *  (시간별 적재 기준 터빈당 연 8,760행). scada_record 가 비어 있는 현재는 체감되지 않으므로
     *  <b>SCADA 적재 시작 전</b>에 아래 중 하나로 정리할 것.
     *  <ol>
     *    <li><b>쿼리 교체(스키마 변경 없음)</b>: {@code SELECT s.turbineId, MAX(s.time) ... GROUP BY s.turbineId}
     *        로 최신 시각만 뽑고(PK 인덱스만 읽음) 그 (터빈,시각) 쌍으로 본 행을 조회하는 2단계.
     *        비용이 터빈 수에만 비례하고 JPQL 이라 DB 이식성도 유지된다. (MySQL LATERAL /
     *        윈도우 함수도 같은 효과지만 네이티브 SQL 이라 H2 등 다른 DB 경로가 깨진다.)</li>
     *    <li><b>최신값 비정규화</b>: 적재 시 {@code turbine_latest}(PK=turbine_id, 터빈당 1행)를 upsert →
     *        조회가 PK 조회로 끝나고 이력 테이블을 아예 건드리지 않는다. 마스터 테이블(turbines)에
     *        컬럼으로 붙이지 말 것 — 제원(정적)과 최신값(고빈도 갱신)이 섞여 락 경합이 생긴다.</li>
     *    <li><b>Redis 표출 캐시</b>: 위 2와 병행 시에만 유효. 단독 사용은 캐시 미스 폴백이 결국 이 쿼리라
     *        문제가 남는다.</li>
     *  </ol>
     *  참고: 파티셔닝/샤딩은 이 문제의 해법이 아니다 — '최신 1건'은 어느 파티션에 있는지 미리 알 수 없어
     *  프루닝이 걸리지 않는다. RANGE(recorded_at) 파티셔닝은 성능이 아니라 <b>보존정책</b>(오래된 구간
     *  DROP PARTITION)용으로 검토할 것.
     */
    @Query("""
            SELECT s FROM ScadaRecord s
            WHERE s.turbineId IN :turbineIds
              AND s.time = (SELECT MAX(s2.time) FROM ScadaRecord s2 WHERE s2.turbineId = s.turbineId)
            """)
    List<ScadaRecord> findLatestByTurbineIds(@Param("turbineIds") Collection<Long> turbineIds);

    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<ScadaRecord> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
