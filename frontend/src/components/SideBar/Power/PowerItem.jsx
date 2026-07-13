import PressureGauge from "./PressureGauge";
import "./PowerItem.css";

function PowerItem({
  title = "장흥 발전소",
  currentOutput = 1005.4,
  currentPower = 412,
  monthPower = 8.7,
  yearPower = 96.4,
  onClick,
}) {
  return (
    <div className="power-item" onClick={onClick}>
      <div className="power-item-title">{title}</div>

      <div className="power-item-label power-item-output-label">
        현재 출력
      </div>

      <PressureGauge value={currentOutput} />

      <div className="power-item-output-value">
        {currentOutput.toFixed(1)} MWh
      </div>

      <div className="power-item-data">
        <div className="power-item-label">금일 발전량</div>
        <div className="power-item-value">{currentPower} MWh</div>
      </div>

      <div className="power-item-data">
        <div className="power-item-label">이번 달 발전량</div>
        <div className="power-item-value">{monthPower.toFixed(1)} GWh</div>
      </div>

      <div className="power-item-data">
        <div className="power-item-label">올해 발전량</div>
        <div className="power-item-value">{yearPower.toFixed(1)} GWh</div>
      </div>
    </div>
  );
}

export default PowerItem;