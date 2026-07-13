import SideTitle from "./SideTitle";
import PowerGaugeGroup from "./Power/PowerGaugeGroup";

import "./TurbineSideBar.css";

function TurbineSideBar({ selectedPlant, selectedTurbine }) {
  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";
  const turbineName = selectedTurbine?.name || "터빈 A";

    const powerGaugeItems = [
    {
      id: 1,
      label: "현재 출력",
      value: 18.4,
      unit: "MWh",
      minValue: 0,
      maxValue: 60,
      subArcLimits: [15, 24, 60],
    },
    {
      id: 2,
      label: "금일 출력",
      value: 412.0,
      unit: "MWh",
      minValue: 0,
      maxValue: 600,
      subArcLimits: [150, 240, 600],
    },
    {
      id: 3,
      label: "금주 출력",
      value: 2.8,
      unit: "GWh",
      minValue: 0,
      maxValue: 5,
      subArcLimits: [1.25, 2, 5],
    },
  ];

  return (
    <div className="sidebar-content turbine-sidebar-content">
      <section className="sidebar-panel turbine-power-panel">
        <SideTitle>
          {plantName} {turbineName} 출력 현황
        </SideTitle>

        <PowerGaugeGroup items={powerGaugeItems} />
      </section>
    </div>
  );
}

export default TurbineSideBar;