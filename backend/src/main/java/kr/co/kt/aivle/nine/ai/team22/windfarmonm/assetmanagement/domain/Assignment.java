package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자-단지 담당 배정(User N:M WindFarm 교차 테이블). user/windFarm 은 id 값으로만 참조한다.
 * 복합 PK (user_id, wind_farm_id) — 배정의 자연키가 곧 두 id 쌍이며, 쌍 유일성을 구조적으로 강제한다.
 * 접근 패턴(user 기준 목록 조회 / 쌍 존재검사)이 모두 PK 프리픽스·정확일치로 처리된다.
 */
@Entity
@Table(name = "assignments")
@IdClass(UserWindFarmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "wind_farm_id", nullable = false)
    private Long windFarmId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Assignment(Long userId, Long windFarmId) {
        this.userId = userId;
        this.windFarmId = windFarmId;
    }

    /** 사용자-단지 담당 배정 생성. created_at 은 영속화 시점에 채워진다. */
    public static Assignment of(Long userId, Long windFarmId) {
        return new Assignment(userId, windFarmId);
    }
}
