import MapSideBar from "./SideBar/MapSideBar";
import PlantSideBar from "./SideBar/PlantSideBar";
import TurbineSideBar from "./SideBar/TurbineSideBar";

function SideBar({
  mode,
  plants = [],
  selectedPlant,
  selectedTurbine,
  onSelectPlant,
  onSelectTurbine,
  onCreateInspectionReport
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
          onSelectTurbine={onSelectTurbine}
          onCreateInspectionReport={onCreateInspectionReport}
        />
      )}

      {mode === "turbine" && (
        <TurbineSideBar
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
        />
      )}
    </aside>
  );
}

export default SideBar;