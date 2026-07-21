import "./ReportTitleToggle.css";

function ReportTitleToggle({
  selectedType,
  onChange,
  firstType = "operation",
  secondType = "repair",
  firstTitle = "발전소 운영 보고서 작성",
  secondTitle = "수리 보고서 작성",
}) {
  return (
    <div className="report-title-toggle">
      <button
        type="button"
        className={selectedType === firstType ? "active" : ""}
        onClick={() => onChange(firstType)}
      >
        {firstTitle}
      </button>

      <button
        type="button"
        className={selectedType === secondType ? "active" : ""}
        onClick={() => onChange(secondType)}
      >
        {secondTitle}
      </button>
    </div>
  );
}

export default ReportTitleToggle;