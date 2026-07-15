import MapMainBar from "./MainBar/MapMainBar";
import PlantMainBar from "./MainBar/PlantMainBar";
import TurbineMainBar from "./MainBar/TurbineMainBar";

function MainBar({ mode, onSelectPlant }) {
  return (
    <aside className="main-bar">
      {mode === "map" && (
        <MapMainBar onSelectPlant={onSelectPlant} />
      )}

      {mode === "plant" && <PlantMainBar />}
      {mode === "turbine" && <TurbineMainBar />}
    </aside>
  );
}

export default MainBar;