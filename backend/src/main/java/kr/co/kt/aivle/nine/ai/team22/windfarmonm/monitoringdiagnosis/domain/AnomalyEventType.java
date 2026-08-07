package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

/**
 * 이상 이벤트 유형.
 */
public enum AnomalyEventType {

    /** 발전하지 않는 상태(정지)가 관측됨. */
    PROLONGED_STOP,

    /** 관측 자체가 들어오지 않음. 수치 지표를 산출할 수 없어 대부분의 컬럼이 비어 있다. */
    DATA_MISSING,

    /** 단지 대비 −8.0%p 넘게 뒤처짐(1차 선별). */
    CHRONIC_SCREENING,

    /** 단지 대비 −11.3%p 넘게 뒤처짐(확정). */
    CHRONIC_CONFIRMED
}
