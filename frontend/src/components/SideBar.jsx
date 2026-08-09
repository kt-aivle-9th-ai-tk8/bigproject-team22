import MapSideBar from "./SideBar/MapSideBar";
import PlantSideBar from "./SideBar/PlantSideBar";
import TurbineSideBar from "./SideBar/TurbineSideBar";

function SideBar({
  mode,
  plants,
  selectedPlant,
  selectedTurbine,
  windFarmDetail,
  onSelectPlant,
  onSelectTurbine,
  onCreateInspectionReport,
  onCreateRepairReport,
}) {
  return (
    <aside className="side-bar">
      {mode === "map" && (
        <MapSideBar
          plants={plants}
          onSelectPlant={onSelectPlant}
        />
      )}

      {mode === "plant" && (
        <PlantSideBar
          selectedPlant={selectedPlant}
          windFarmDetail={windFarmDetail}
          onSelectTurbine={onSelectTurbine}
          onCreateRepairReport={onCreateRepairReport}
        />
      )}

      {mode === "turbine" && (
        <TurbineSideBar
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          onCreateInspectionReport={onCreateInspectionReport}
        />
      )}
    </aside>
  );
}

export default SideBar;