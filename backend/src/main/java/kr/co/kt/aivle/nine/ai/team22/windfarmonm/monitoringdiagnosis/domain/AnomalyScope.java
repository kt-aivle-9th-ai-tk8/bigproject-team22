package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

/**
 * 결측의 원인 구분. {@link AnomalyEventType#DATA_MISSING} 에서만 의미가 있다.
 * <p>
 * 집계 단위가 아니라 <b>원인 라벨</b>이다 — {@link #FARM} 이어도 이벤트는 호기별로 한 건씩 생긴다.
 */
public enum AnomalyScope {

    /** 같은 단지의 다른 호기도 함께 결측이다 → 수집·통신 장애로 본다. */
    FARM,

    /** 해당 호기만 결측이다 → 설비 문제로 본다. */
    TURBINE
}
