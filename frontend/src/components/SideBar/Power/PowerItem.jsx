import PowerGauge from "./PowerGauge";
import "./PowerItem.css";

function PowerItem({
  title,
  currentOutput = 1005.4,
  currentPower = 412,
  monthPower = 8.7,
  onClick,
}) {
  const formatPower = (value) => {
    const numberValue = Number(value) || 0;

    if (numberValue >= 1000) {
      return `${(numberValue / 1000).toFixed(2)} MWh`;
    }

    return `${numberValue.toFixed(2)} kWh`;
  };

  return (
    <div
      className={`power-item ${onClick ? "power-item-clickable" : ""}`}
      onClick={onClick}
    >
      {title && (
        <div className="power-item-title">
          {title}
        </div>
      )}

      <div className="power-item-label power-item-output-label">
        현재 출력
      </div>

      <PowerGauge value={currentOutput} />

      <div className="power-item-output-value">
        {formatPower(currentOutput)}
      </div>

      <div className="power-item-data">
        <div className="power-item-label">
          금일 발전량
        </div>
        <div className="power-item-value">
          {formatPower(currentPower)}
        </div>
      </div>

      <div className="power-item-data">
        <div className="power-item-label">
          이번 달 발전량
        </div>
        <div className="power-item-value">
          {formatPower(monthPower)}
        </div>
      </div>
    </div>
  );
}

export default PowerItem;