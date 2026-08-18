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
  isOperationReportPending,
  isInspectionReportCreating,
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
          onCreateInspectionReport={onCreateInspectionReport}
          onCreateOperationReport={onCreateOperationReport}
          isOperationReportPending={isOperationReportPending}
          isInspectionReportCreating={isInspectionReportCreating}
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
          isOperationReportPending={isOperationReportPending}
          isInspectionReportCreating={isInspectionReportCreating}
        />
      )}
    </aside>
  );
}

export default SideBar;