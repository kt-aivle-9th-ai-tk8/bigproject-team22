import MapMainBar from "./MainBar/MapMainBar";
import PlantMainBar from "./MainBar/PlantMainBar";
import TurbineMainBar from "./MainBar/TurbineMainBar";

function MainBar({
  mode,
  plants = [],
  selectedPlant,
  selectedTurbine,
  isPlantsLoading,
  plantsError,
  onSelectPlant,
  onSelectTurbine,
}) {
  const turbines =
    selectedPlant?.turbines || [];

  return (
    <aside className="main-bar">
      {mode === "map" && (
        <MapMainBar
          plants={plants}
          isLoading={isPlantsLoading}
          error={plantsError}
          onSelectPlant={onSelectPlant}
        />
      )}

      {mode === "plant" && (
        <PlantMainBar
          turbines={turbines}
          onSelectTurbine={onSelectTurbine}
        />
      )}

      {mode === "turbine" && (
        <TurbineMainBar
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
        />
      )}
    </aside>
  );
}

export default MainBar;