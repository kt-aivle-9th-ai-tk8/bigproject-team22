import "./TurbineReportList.css";


export const REPORT_TYPE = {
  TURBINE_OPERATION: "turbine_operation",
  DEFECT_DIAGNOSIS: "defect_diagnosis",
  ANOMALY_EVENT: "anomaly_event",
};


const REPORT_TYPE_LABEL = {
  [REPORT_TYPE.TURBINE_OPERATION]: "터빈별 운영 리포트",
  [REPORT_TYPE.DEFECT_DIAGNOSIS]: "결함 진단 리포트",
  [REPORT_TYPE.ANOMALY_EVENT]: "이상 감지 리포트",
};


const REPORT_TYPE_ICON = {
  [REPORT_TYPE.TURBINE_OPERATION]: "📊",
  [REPORT_TYPE.DEFECT_DIAGNOSIS]: "🚨",
  [REPORT_TYPE.ANOMALY_EVENT]: "⚠️",
};


function TurbineReportList({
  items = [],
  onSelectReport,
  onMoreClick,
}) {
  return (
    <div className="turbine-report-list">
      <div className="turbine-report-items">
        {items.map((item) => (
          <div
            className="turbine-report-item"
            key={item.id}
            onClick={() =>
              onSelectReport?.(item)
            }
          >
            <div className="turbine-report-text">
              <div className="turbine-report-title">
                {item.title}
              </div>

              {item.subtitle && (
                <div className="turbine-report-subtitle">
                  {item.subtitle}
                </div>
              )}
            </div>


            <div
              className={`turbine-report-icon ${item.status?.toLowerCase()}`}
              title={
                REPORT_TYPE_LABEL[
                  item.status
                ]
              }
            >
              {
                REPORT_TYPE_ICON[
                  item.status
                ]
              }
            </div>
          </div>
        ))}
      </div>


      <button
        className="turbine-report-more-button"
        type="button"
        onClick={onMoreClick}
      >
        전체보기
      </button>
    </div>
  );
}


export default TurbineReportList;