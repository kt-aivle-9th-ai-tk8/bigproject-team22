import { useState } from "react";
import { useNavigate } from "react-router-dom";

import SideTitle from "./SideTitle";
import PowerGaugeGroup from "./Power/PowerGaugeGroup";
import ReportBox from "./Report/ReportBox";
import ReportTitleToggle from "./Report/ReportTitleToggle";
import InspectionReportBox from "./Report/InspectionReportBox";
import TurbineReportList from "./Report/TurbineReportList";

import "./TurbineSideBar.css";

function TurbineSideBar({
  selectedPlant,
  selectedTurbine,
  turbineDetail,
  reportItems = [],
  onCreateInspectionReport,
  onCreateOperationReport,
}) {
  const navigate = useNavigate();

  const [reportMode, setReportMode] = useState("operation");

  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";
  const turbineName = selectedTurbine?.name || "터빈 A";
  const today = new Date().toISOString().slice(0, 10);

  const powerGaugeItems = [
    {
      id: 1,
      label: "현재 출력",
      value:
        (turbineDetail?.power?.current_power ?? 0),
      unit: "kW",
      minValue: 0,
      maxValue: 2000,
      subArcLimits: [800, 900, 2000],
    },
    {
      id: 2,
      label: "금일 출력",
      value:
        (turbineDetail?.power?.today_power ?? 0) / 1000,
      unit: "MWh",
      minValue: 0,
      maxValue: 48,
      subArcLimits: [19.2, 21.6, 48],
    },
    {
      id: 3,
      label: "금월 출력",
      value:
        (turbineDetail?.power?.month_power ?? 0) / 1000,
      unit: "MWh",
      minValue: 0,
      maxValue: 1200,
      subArcLimits: [400, 480, 1000],
    },
  ];

  const handleSelectReport = (report) => {
    navigate(`/reports/${report.id}/edit`, {
      state: {
        report,
      },
    });
  };


  const handleReportList = () => {
    navigate("/reportlist");
  };

  return (
    <div className="sidebar-content turbine-sidebar-content">
      <section className="sidebar-panel turbine-power-panel">
        <SideTitle>
          {plantName} {turbineName} 터빈 출력 현황
        </SideTitle>

        <PowerGaugeGroup items={powerGaugeItems} />
      </section>

      <div className="sidebar-divider-wrap turbine-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel turbine-report-panel">
        <SideTitle>터빈보고서 리스트</SideTitle>

        <TurbineReportList
          items={reportItems}
          onSelectReport={handleSelectReport}
          onMoreClick={handleReportList}
        />
      </section>

      <div className="sidebar-divider-wrap turbine-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel plant-report-panel">
        <ReportTitleToggle
          selectedType={reportMode}
          onChange={setReportMode}
          firstType="operation"
          secondType="inspection"
          firstTitle="터빈 운영 보고서"
          secondTitle="점검 보고서"
        />

        {reportMode === "operation" && (
          <ReportBox
            reportMode={reportMode}
            startDate={today}
            endDate={today}
            onCreateReport={onCreateOperationReport}
          />
        )}

        {reportMode === "inspection" && (
          <InspectionReportBox
            turbineName={turbineName}
            turbineOptions={
              selectedTurbine ? [selectedTurbine] : []
            }
            initialData={{
              turbines: [turbineName],
              fixedTurbine: true,
            }}
            onCreateReport={(reportData) => {
              onCreateInspectionReport?.({
                ...reportData,
                plantId: selectedPlant?.id,
                plantName,
                turbineId: selectedTurbine?.id,
                turbineName,
              });
            }}
          />
        )}
        
      </section>
    </div>
  );
}

export default TurbineSideBar;