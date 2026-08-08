package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain;

/**
 * 보고서 생성 상태.
 * <p>
 * 본문은 외부 에이전트가 만들기 때문에 "행은 있으나 내용이 없는" 구간이 존재한다. 그 구간을 두 단계로 나눈다.
 * <p>
 * <b>실패 상태는 두지 않는다.</b> 에이전트가 끝내 실패하면 {@link #PROCESSING} 에 남는데, 이를 구분·회수하는
 * 장치는 후속 과제다. 그래서 상태를 근거로 수정/삭제를 막지 않는다 — 막으면 실패한 보고서를 영영 손댈 수 없다.
 */
public enum ReportStatus {

    /** 식별자만 선점된 상태. 결함 보고서가 점검 업로드를 기다리는 구간이 여기에 해당한다. */
    PENDING,

    /** 에이전트에 생성을 요청한 상태. */
    PROCESSING,

    /** 본문 적재 완료. */
    GENERATED
}
