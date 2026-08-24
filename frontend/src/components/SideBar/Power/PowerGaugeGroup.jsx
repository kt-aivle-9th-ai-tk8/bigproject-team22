import PowerGauge from "./PowerGauge";
import "./PowerGaugeGroup.css";

const DEFAULT_LABELS = ["현재 출력", "금일 출력", "금주 출력"];

function PowerGaugeGroup({ items }) {
  return (
    <div
      className="power-gauge-group"
      style={{ "--power-gauge-count": items.length }}
    >
      {items.map((item, index) => (
        <div className="power-gauge-group-item" key={item.id}>
          <div className="power-gauge-group-content">
            <div className="power-gauge-group-label">
              {item.label || DEFAULT_LABELS[index]}
            </div>

            <PowerGauge
              value={item.value}
              minValue={item.minValue}
              maxValue={item.maxValue}
              subArcLimits={item.subArcLimits}
            />

            <div className="power-gauge-group-value">
              {item.value.toFixed(1)} {item.unit || "MWh"}
            </div>
          </div>

          {index !== items.length - 1 && (
            <div className="power-gauge-divider" />
          )}
        </div>
      ))}
    </div>
  );
}

export default PowerGaugeGroup;