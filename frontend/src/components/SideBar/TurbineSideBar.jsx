import { useState } from "react";

import SideTitle from "./SideTitle";
import PowerGaugeGroup from "./Power/PowerGaugeGroup";
import ReportBox from "./Report/ReportBox";
import ReportTitleToggle from "./Report/ReportTitleToggle";
import InspectionReportBox from "./Report/InspectionReportBox";
import TurbineReportList, { REPORT_TYPE } from "./Report/TurbineReportList";

import "./TurbineSideBar.css";

function TurbineSideBar({
  selectedPlant,
  selectedTurbine,
  onCreateInspectionReport,
}) {
  const [reportMode, setReportMode] = useState("operation");

  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";
  const turbineName = selectedTurbine?.name || "터빈 A";
  const today = new Date().toISOString().slice(0, 10);

  const powerGaugeItems = [
    {
      id: 1,
      label: "현재 출력",
      value: 18.4,
      unit: "MWh",
      minValue: 0,
      maxValue: 60,
      subArcLimits: [15, 24, 60],
    },
    {
      id: 2,
      label: "금일 출력",
      value: 412.0,
      unit: "MWh",
      minValue: 0,
      maxValue: 600,
      subArcLimits: [150, 240, 600],
    },
    {
      id: 3,
      label: "금월 출력",
      value: 2.8,
      unit: "GWh",
      minValue: 0,
      maxValue: 5,
      subArcLimits: [1.25, 2, 5],
    },
  ];

  const reportItems = [
    {
      id: 1,
      date: "2026-07-09",
      time: "14:20",
      status: REPORT_TYPE.FAULT,
    },
    {
      id: 2,
      date: "2026-07-08",
      time: "12:20",
      status: REPORT_TYPE.FAULT,
    },
    {
      id: 3,
      date: "2026-07-06",
      time: "14:20",
      status: REPORT_TYPE.OPERATION,
    },
    {
      id: 4,
      date: "2026-07-02",
      time: "14:00",
      status: REPORT_TYPE.WARNING,
    },
    {
      id: 5,
      date: "2026-06-24",
      time: "10:20",
      status: REPORT_TYPE.REPAIR,
    },
  ];

  return (
    <div className="sidebar-content turbine-sidebar-content">
      <section className="sidebar-panel turbine-power-panel">
        <SideTitle>
          {plantName} {turbineName} 출력 현황
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
          onSelectReport={(report) => {
            console.log("선택한 보고서:", report);
          }}
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
            onCreateReport={(reportData) => {
              console.log("터빈 운영 보고서 생성 데이터:", {
                ...reportData,
                plantId: selectedPlant?.id,
                plantName,
                turbineId: selectedTurbine?.id,
                turbineName,
              });
            }}
          />
        )}

        {reportMode === "inspection" && (
          <InspectionReportBox
            turbineName={turbineName}
            turbineOptions={[turbineName]}
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
                turbines: [turbineName],
              });
            }}
          />
        )}
        
      </section>
    </div>
  );
}

export default TurbineSideBar;