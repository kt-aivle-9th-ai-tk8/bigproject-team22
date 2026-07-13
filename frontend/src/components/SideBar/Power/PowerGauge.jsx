import GaugeComponent from "react-gauge-component";
import "./PowerGauge.css";

const POINT_COLOR = "#0B50D1";

function PowerGauge({
  value = 18.4,
  minValue = 0,
  maxValue = 60,
  subArcLimits = [15, 24, 60],
}) {
  const subArcs = subArcLimits.map((limit) => ({
    limit,
    color: POINT_COLOR,
  }));

  return (
    <div className="power-gauge">
      <GaugeComponent
        value={value}
        minValue={minValue}
        maxValue={maxValue}
        type="semicircle"
        labels={{
          valueLabel: {
            hide: true,
          },
          tickLabels: {
            hideMinMax: true,
            ticks: [],
          },
        }}
        arc={{
          width: 0.18,
          padding: 0.02,
          cornerRadius: 1,
          subArcs,
        }}
        pointer={{
          type: "needle",
          color: POINT_COLOR,
        }}
      />
    </div>
  );
}

export default PowerGauge;