import MapMainBar from "./MainBar/MapMainBar";
import PlantMainBar from "./MainBar/PlantMainBar";
import TurbineMainBar from "./MainBar/TurbineMainBar";

function MainBar({
  mode,
  plants = [],
  turbines = [],
  selectedPlant,
  selectedTurbine,
  turbineDetail,
  isPlantsLoading,
  plantsError,
  isWindFarmDetailLoading,
  windFarmDetailError,
  onSelectPlant,
  onSelectTurbine,
}) {
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
          isLoading={isWindFarmDetailLoading}
          error={windFarmDetailError}
          onSelectTurbine={onSelectTurbine}
        />
      )}

      {mode === "turbine" && (
        <TurbineMainBar
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          turbineDetail={turbineDetail}
        />
      )}
    </aside>
  );
}

export default MainBar;