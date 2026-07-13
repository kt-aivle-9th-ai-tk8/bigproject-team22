import GaugeComponent from "react-gauge-component";
import "./PressureGauge.css";

const POINT_COLOR = "#0B50D1";

function PressureGauge({ value = 1005.4 }) {
  return (
    <div className="pressure-gauge">
      <GaugeComponent
        value={value}
        minValue={0}
        maxValue={60}
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
          subArcs: [
            { limit: 15, color: POINT_COLOR },
            { limit: 24, color: POINT_COLOR },
            { limit: 60, color: POINT_COLOR },
          ],
        }}
        pointer={{
          type: "needle",
          color: POINT_COLOR,
        }}
      />
    </div>
  );
}

export default PressureGauge;