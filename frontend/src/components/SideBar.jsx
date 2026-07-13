import MapSideBar from "./sidebar/MapSideBar";
import PlantSideBar from "./sidebar/PlantSideBar";

function SideBar({ mode, selectedPlant }) {
  return (
    <aside className="side-bar">
      {mode === "map" ? (
        <MapSideBar />
      ) : (
        <PlantSideBar selectedPlant={selectedPlant} />
      )}
    </aside>
  );
}

export default SideBar;