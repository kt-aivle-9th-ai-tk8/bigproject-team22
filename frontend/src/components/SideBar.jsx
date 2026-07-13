import MapSideBar from "./SideBar/MapSideBar";
import PlantSideBar from "./SideBar/PlantSideBar";

function SideBar({ mode, selectedPlant, onSelectPlant }) {
  return (
    <aside className="side-bar">
      {mode === "map" && (
        <MapSideBar onSelectPlant={onSelectPlant} />
      )}

      {mode === "plant" && (
        <PlantSideBar selectedPlant={selectedPlant} />
      )}
      {mode === "turbine" && (
        <TurbineSideBar
          selectedPlant={selectedPlant}
        />
      )}
    </aside>
  );
}

export default SideBar;