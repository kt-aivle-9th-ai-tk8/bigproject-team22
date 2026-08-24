import "./TurbineItem.css";

export const TURBINE_STATUS = {
  NORMAL: "NORMAL",
  ZERO_POWER: "ZERO_POWER",
  NO_DATA: "NO_DATA",
};

const STATUS_LABEL = {
  [TURBINE_STATUS.NORMAL]: "정상",
  [TURBINE_STATUS.ZERO_POWER]: "발전량 0",
  [TURBINE_STATUS.NO_DATA]: "발전량 데이터 없음",
};

const STATUS_COLOR_CLASS = {
  [TURBINE_STATUS.NORMAL]: "green",
  [TURBINE_STATUS.ZERO_POWER]: "red",
  [TURBINE_STATUS.NO_DATA]: "yellow",
};

function TurbineItem({
  name = "터빈 A",
  status = TURBINE_STATUS.NORMAL,
  abnormalDetected = false,
  onClick,
}) {
  const statusClass = STATUS_COLOR_CLASS[status] || "green";
  const statusLabel = STATUS_LABEL[status] || "정상";

  return (
    <div
      className={`turbine-item ${onClick ? "turbine-item-clickable" : ""}`}
      onClick={onClick}
    >
      <div className="turbine-item-left">
        <span className="turbine-item-name">{name}</span>
        <span className={`turbine-status-dot ${statusClass}`} />
      </div>

      <div className="turbine-item-right">
        {abnormalDetected && (
          <span
            className="turbine-abnormal-icon"
            aria-label="이상 감지"
            title="이상 감지"
          >
            ⚠️
          </span>
        )}
      </div>
    </div>
  );
}

export default TurbineItem;