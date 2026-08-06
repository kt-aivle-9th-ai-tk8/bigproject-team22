package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * (user_id, wind_farm_id) 복합 PK 클래스. assignments 의 자연키(@IdClass 용도).
 * 필드명/타입은 {@link Assignment} 의 @Id 필드(userId, windFarmId)와 정확히 일치해야 한다.
 */
@NoArgsConstructor
@EqualsAndHashCode
public class UserWindFarmId implements Serializable {

    private Long userId;

    private Long windFarmId;
}
