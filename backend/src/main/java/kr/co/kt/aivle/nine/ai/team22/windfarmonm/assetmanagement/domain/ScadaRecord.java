package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SCADA 계측 레코드(발전량 원천 데이터). RDS 테이블이며, 발전량 조회가
 * 실제로 사용하는 컬럼(측정시점/출력)만 매핑한다. 순시/실시간(raw) 발전량 조회의 소스이다.
 * 복합 PK (turbine_id, recorded_at) — 측정 시각(time)을 그대로 키로 사용한다(터빈당 시각당 1건).
 */
@Entity
@Table(name = "scada_record")
@IdClass(TurbineInstantId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScadaRecord {

    @Id
    @Column(name = "turbine_id", nullable = false)
    private Long turbineId;

    @Id
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime time;

    /** 발전 출력 (kW) */
    @Column(name = "power_output")
    private Double powerOutput;
}
