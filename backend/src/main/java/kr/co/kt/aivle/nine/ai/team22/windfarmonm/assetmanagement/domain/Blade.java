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
 * 블레이드. 소속 터빈은 turbineId 로만 참조한다.
 */
@Entity
@Table(name = "blades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Blade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "blade_id")
    private Long id;

    @Column(name = "turbine_id", nullable = false)
    private Long turbineId;

    /** 블레이드 태그 (A/B/C) */
    @Column(name = "blade_tag", nullable = false, length = 10)
    private String tag;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
