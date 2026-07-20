import "./ReportTitleToggle.css";

function ReportTitleToggle({ selectedType, onChange }) {
  return (
    <div className="report-title-toggle">
      <button
        type="button"
        className={selectedType === "operation" ? "active" : ""}
        onClick={() => onChange("operation")}
      >
        발전소 운영 보고서 작성
      </button>

      <button
        type="button"
        className={selectedType === "inspection" ? "active" : ""}
        onClick={() => onChange("inspection")}
      >
        점검 보고서 작성
      </button>
    </div>
  );
}

export default ReportTitleToggle;