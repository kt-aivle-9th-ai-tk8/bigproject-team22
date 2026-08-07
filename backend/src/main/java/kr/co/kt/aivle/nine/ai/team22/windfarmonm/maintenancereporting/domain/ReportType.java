package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain;

/**
 * 보고서 유형. 어떤 대상을 다루는지, 무엇이 생성을 유발했는지, 사용자가 직접 요청할 수 있는지를 나타낸다.
 */
public enum ReportType {

    /** 단지 운영 보고서. 대상 터빈이 없다. 사용자가 직접 생성한다. */
    WIND_FARM_OPERATION(false, true),

    /** 터빈 운영 보고서. 사용자가 직접 생성한다. */
    TURBINE_OPERATION(true, true),

    /** 결함 진단 보고서. 점검(inspection) 생성 시 함께 만들어진다 — 공개 생성 API 로는 만들 수 없다. */
    DEFECT_DIAGNOSIS(true, false),

    /** 이상 감지 보고서. 이상감지 배치가 자동 생성한다 — 공개 생성 API 로는 만들 수 없다. */
    ANOMALY_EVENT(true, false);

    private final boolean turbineScoped;
    private final boolean userRequestable;

    ReportType(boolean turbineScoped, boolean userRequestable) {
        this.turbineScoped = turbineScoped;
        this.userRequestable = userRequestable;
    }

    /** 대상 터빈이 반드시 지정되어야 하는 유형인지. 단지 단위 보고서만 false 다. */
    public boolean requiresTurbine() {
        return turbineScoped;
    }

    /**
     * 사용자가 공개 생성 API 로 직접 요청할 수 있는 유형인지.
     * <p>
     * 결함·이상 보고서는 각각 점검·이상감지가 유발 이벤트와 함께 자동 생성한다. 공개 API 로 만들면
     * 유발 이벤트가 없는 반쪽짜리 보고서가 생기므로 허용하지 않는다.
     */
    public boolean isUserRequestable() {
        return userRequestable;
    }
}
