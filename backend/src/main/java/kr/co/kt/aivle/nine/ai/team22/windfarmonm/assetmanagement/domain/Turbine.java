package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 터빈. 소속 단지/모델은 id 값으로 참조한다(연관관계 매핑 대신 id 참조).
 */
@Entity
@Table(name = "turbines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Turbine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turbine_id")
    private Long id;

    @Column(name = "wind_farm_id", nullable = false)
    private Long windFarmId;

    @Column(name = "turbine_model_id", nullable = false)
    private Long turbineModelId;

    /** 현장코드 (예: U1) */
    @Column(name = "turbine_code", nullable = false, length = 20)
    private String code;

    @Column(name = "turbine_latitude", nullable = false)
    private Double latitude;

    @Column(name = "turbine_longitude", nullable = false)
    private Double longitude;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
