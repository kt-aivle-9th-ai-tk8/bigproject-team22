package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

/**
 * 아웃박스 발행 상태(V6 스키마의 status 값과 동일).
 */
public enum OutboxStatus {

    /** 기록됨 — 릴레이가 아직 집어가지 않았다. */
    PENDING,

    /** 릴레이가 발행(추론 요청 발사)을 마쳤다 — 결과 통보 대기 중. */
    PUBLISHED,

    /** 결과 통보를 받아 처리(결함 적재)까지 끝났다. V6 주석의 3종에 앱 차원에서 추가한 값(컬럼은 VARCHAR). */
    COMPLETED,

    /** 발행하지 못했거나 추론이 실패했다 — 운영자 확인 대상. */
    FAILED
}
