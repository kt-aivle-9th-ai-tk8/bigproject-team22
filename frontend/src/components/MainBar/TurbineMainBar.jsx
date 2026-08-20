import Turbine3DSimulation from "./Simulation/Turbine3DSimulation";
import "./TurbineMainBar.css";

function TurbineMainBar({
  selectedPlant,
  selectedTurbine,
  turbineDetail,
  onRunSimulation,
}) {
  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";
  const turbineName = selectedTurbine?.name || "터빈 A";

  return (
    <div className="turbine-main-bar">
      <Turbine3DSimulation
        plantName={plantName}
        turbineName={turbineName}
        turbineDetail={turbineDetail}
        onRunSimulation={onRunSimulation}
      />
    </div>
  );
}

export default TurbineMainBar;