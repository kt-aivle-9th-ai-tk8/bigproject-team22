import "./TurbineItem.css";

export const TURBINE_STATUS = {
  NORMAL: "NORMAL",
  WARNING: "WARNING",
  ALERT: "ALERT",
};

const STATUS_COLOR_CLASS = {
  [TURBINE_STATUS.NORMAL]: "green",
  [TURBINE_STATUS.WARNING]: "yellow",
  [TURBINE_STATUS.ALERT]: "red",
};

function TurbineItem({
  name = "터빈 A",
  status = TURBINE_STATUS.NORMAL,
  alertCount = 0,
  hasEmergency = false,
  onClick,
}) {
  const statusClass = STATUS_COLOR_CLASS[status] || "green";

  return (
    <div className="turbine-item" onClick={onClick}>
      <div className="turbine-item-left">
        <span className="turbine-item-name">{name}</span>
        <span className={`turbine-status-dot ${statusClass}`} />
      </div>

      <div className="turbine-item-right">
        {hasEmergency && (
          <span className="turbine-emergency-icon">🚨</span>
        )}
        {alertCount > 0 && (
          <span className="turbine-alert-count">⚠️{alertCount}</span>
        )}
      </div>
    </div>
  );
}

export default TurbineItem;