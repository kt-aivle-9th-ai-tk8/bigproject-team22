import MapSideBar from "./SideBar/MapSideBar";
import PlantSideBar from "./SideBar/PlantSideBar";
import TurbineSideBar from "./SideBar/TurbineSideBar";
import InfoFooter from "./SideBar/InfoFooter/InfoFooter";

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
  onSelectNotification,
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
          onSelectNotification={onSelectNotification}
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

      <div className="sidebar-info-footer">
        <InfoFooter />
      </div>
    </aside>
  );
}

export default SideBar;