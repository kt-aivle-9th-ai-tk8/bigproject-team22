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
 * 일별 발전량 집계(cron 롤업, RDS 직접 조회). 복합 PK (turbine_id, stat_at). stat_at(time)은 해당 일 00:00.
 * 발전량 값 타입은 {@link ScadaRecord#getPowerOutput()} 과 동일하게 Double/DOUBLE 로 맞춘다.
 */
@Entity
@Table(name = "daily_generation")
@IdClass(TurbineInstantId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyGeneration {

    @Id
    @Column(name = "turbine_id", nullable = false)
    private Long turbineId;

    @Id
    @Column(name = "stat_at", nullable = false)
    private LocalDateTime time;

    /** 일별 누적 발전량 (kWh) */
    @Column(name = "daily_power_output")
    private Double dailyPowerOutput;
}
