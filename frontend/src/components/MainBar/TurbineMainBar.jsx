import Turbine3DSimulation from "./Simulation/Turbine3DSimulation";
import "./TurbineMainBar.css";

function TurbineMainBar({
  selectedPlant,
  selectedTurbine,
  onRunSimulation,
}) {
  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";
  const turbineName = selectedTurbine?.name || "터빈 A";

  return (
    <div className="turbin e-main-bar">
      <Turbine3DSimulation
        plantName={plantName}
        turbineName={turbineName}
        onRunSimulation={(simulationData) => {
          onRunSimulation?.({
            ...simulationData,
            plantId: selectedPlant?.id,
            turbineId: selectedTurbine?.id,
          });
        }}
      />
    </div>
  );
}

export default TurbineMainBar;