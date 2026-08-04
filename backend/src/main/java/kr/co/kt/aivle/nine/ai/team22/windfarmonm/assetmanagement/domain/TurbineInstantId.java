package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * (turbine_id, time) 복합 PK 클래스. scada_record / daily_generation / monthly_generation 이 공유한다(@IdClass 용도).
 * 필드명/타입은 각 엔티티의 @Id 필드(turbineId, time)와 정확히 일치해야 한다.
 * 컬럼명은 엔티티마다 @Column 으로 다르게 매핑한다(recorded_at / stat_at) — @IdClass 는 컬럼명이 아니라 필드명으로 매칭한다.
 */
@NoArgsConstructor
@EqualsAndHashCode
public class TurbineInstantId implements Serializable {

    private Long turbineId;

    private LocalDateTime time;
}
