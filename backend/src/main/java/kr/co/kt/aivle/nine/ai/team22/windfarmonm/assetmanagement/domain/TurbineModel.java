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

/**
 * 터빈 모델(제원). Turbine 은 turbineModelId 로만 참조한다.
 */
@Entity
@Table(name = "turbine_models")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TurbineModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turbine_model_id")
    private Long id;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "manufacturer", length = 50)
    private String manufacturer;

    @Column(name = "rated_power")
    private Integer ratedPower;

    @Column(name = "rotor_diameter")
    private Double rotorDiameter;

    @Column(name = "hub_height")
    private Double hubHeight;

    @Column(name = "blade_length")
    private Double bladeLength;

    @Column(name = "cut_in_speed")
    private Double cutInSpeed;

    @Column(name = "rated_speed")
    private Double ratedSpeed;

    @Column(name = "cut_out_speed")
    private Double cutOutSpeed;
}
