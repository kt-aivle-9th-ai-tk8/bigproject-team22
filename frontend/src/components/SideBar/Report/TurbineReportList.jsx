import "./TurbineReportList.css";

export const REPORT_TYPE = {
  FAULT: "FAULT",
  WARNING: "WARNING",
  OPERATION: "OPERATION",
  REPAIR: "REPAIR",
};

const REPORT_TYPE_LABEL = {
  [REPORT_TYPE.FAULT]: "결함 보고서",
  [REPORT_TYPE.WARNING]: "경고 보고서",
  [REPORT_TYPE.OPERATION]: "운영 보고서",
  [REPORT_TYPE.REPAIR]: "수리 보고서",
};

const REPORT_TYPE_ICON = {
  [REPORT_TYPE.FAULT]: "🚨",
  [REPORT_TYPE.WARNING]: "⚠️",
  [REPORT_TYPE.OPERATION]: "📊",
  [REPORT_TYPE.REPAIR]: "🔧",
};

function TurbineReportList({ items = [], onSelectReport }) {
  return (
    <div className="turbine-report-list">
      <div className="turbine-report-title">
        터빈보고서 리스트
      </div>

      <div className="turbine-report-items">
        {items.map((item) => (
          <div
            className="turbine-report-item"
            key={item.id}
            onClick={() => onSelectReport?.(item)}
          >
            <div className="turbine-report-date">
              {item.date} | {item.time}
            </div>

            <div
              className={`turbine-report-icon ${item.status?.toLowerCase()}`}
              title={REPORT_TYPE_LABEL[item.status]}
            >
              {REPORT_TYPE_ICON[item.status]}
            </div>
          </div>
        ))}
      </div>

      <button className="turbine-report-more-button">
        전체보기
      </button>
    </div>
  );
}

export default TurbineReportList;