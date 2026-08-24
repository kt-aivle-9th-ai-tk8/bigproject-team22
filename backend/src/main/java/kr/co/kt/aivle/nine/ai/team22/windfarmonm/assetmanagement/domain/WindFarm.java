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
 * 풍력 발전단지. 다른 BC(User 등)는 id 값으로만 참조한다.
 */
@Entity
@Table(name = "wind_farm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WindFarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wind_farm_id")
    private Long id;

    @Column(name = "wind_farm_name", nullable = false, length = 100)
    private String name;

    @Column(name = "wind_farm_latitude", nullable = false)
    private Double latitude;

    @Column(name = "wind_farm_longitude", nullable = false)
    private Double longitude;

    /** 단지 전체 용량 (kW) */
    @Column(name = "capacity")
    private Double capacity;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(name = "address", length = 200)
    private String address;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 실시간 날씨 조회 대상 기상청 AWS 관측소 지점번호 */
    @Column(name = "aws_station_id")
    private Long awsStationId;

    /** 실시간 날씨 조회 대상 기상청 ASOS 관측소 지점번호 */
    @Column(name = "asos_station_id")
    private Long asosStationId;
}
