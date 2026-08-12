import MapSideBar from "./SideBar/MapSideBar";
import PlantSideBar from "./SideBar/PlantSideBar";
import TurbineSideBar from "./SideBar/TurbineSideBar";

function SideBar({
  mode,
  plants,
  selectedPlant,
  selectedTurbine,
  windFarmDetail,
  turbineDetail,
  turbineReportItems,
  notifications,
  onSelectPlant,
  onSelectTurbine,
  onCreateInspectionReport,
  onCreateOperationReport,
}) {
  return (
    <aside className="side-bar">
      {mode === "map" && (
        <MapSideBar
          plants={plants}
          notifications={notifications}
          onSelectPlant={onSelectPlant}
        />
      )}

      {mode === "plant" && (
        <PlantSideBar
          selectedPlant={selectedPlant}
          windFarmDetail={windFarmDetail}
          onSelectTurbine={onSelectTurbine}
          onCreateOperationReport={onCreateOperationReport}
        />
      )}

      {mode === "turbine" && (
        <TurbineSideBar
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          turbineDetail={turbineDetail}
          reportItems={turbineReportItems}
          onCreateInspectionReport={onCreateInspectionReport}
          onCreateOperationReport={onCreateOperationReport}
        />
      )}
    </aside>
  );
}

export default SideBar;