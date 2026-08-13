package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

/**
 * 아웃박스 발행 상태(V6 스키마의 status 값과 동일).
 */
public enum OutboxStatus {

    /** 기록됨 — 릴레이가 아직 집어가지 않았다. */
    PENDING,

    /** 릴레이가 발행(추론 요청 발사)을 마쳤다. */
    PUBLISHED,

    /** 재시도로도 발행하지 못했다 — 운영자 확인 대상. */
    FAILED
}
