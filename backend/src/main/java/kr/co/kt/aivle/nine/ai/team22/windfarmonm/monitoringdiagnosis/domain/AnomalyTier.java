package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

/**
 * 이상 감시 계층. 무엇을 근거로 판정했는지가 다르며, 계층별로 채워지는 지표가 갈린다.
 */
public enum AnomalyTier {

    /** 급성. 정지·결측처럼 관측 자체로 드러나는 사안. z-score·30일 에너지비는 산출되지 않는다. */
    A,

    /** 만성. 단지 대비 상대 성능이 지속적으로 뒤처지는 사안. 30일 창 지표로 판정한다. */
    B
}
