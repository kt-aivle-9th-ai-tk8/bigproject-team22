import { useState } from "react";

import TurbineList from "./Turbine/TurbineList";
import PlantSummaryGroup from "./PlantSummaryGroup";
import ReportBox from "./Report/ReportBox";
import SideTitle from "./SideTitle";
import ReportTitleToggle from "./Report/ReportTitleToggle";
import InspectionReportBox from "./Report/InspectionReportBox";

import { TURBINE_STATUS } from "./Turbine/TurbineItem";
import { WEATHER_TYPE } from "./Weather/WeatherItem";

import "./PlantSideBar.css";

function PlantSideBar({
  selectedPlant,
  onSelectTurbine,
  onCreateInspectionReport,
}) {
  const [reportMode, setReportMode] = useState("operation");

  const plantName = selectedPlant?.title || selectedPlant?.name;
  const today = new Date().toISOString().slice(0, 10);

  const turbineItems = [
    {
      id: 1,
      name: "터빈 A",
      status: TURBINE_STATUS.ALERT,
      alertCount: 6,
      hasEmergency: true,
    },
    {
      id: 2,
      name: "터빈 B",
      status: TURBINE_STATUS.WARNING,
      alertCount: 3,
      hasEmergency: false,
    },
    {
      id: 3,
      name: "터빈 C",
      status: TURBINE_STATUS.ALERT,
      alertCount: 2,
      hasEmergency: true,
    },
  ];

  const weatherInfo = {
    title: plantName || "발전소",
    weatherType: WEATHER_TYPE.RAIN,
    temperature: 32.0,
    windSpeed: 5.0,
  };

  const powerInfo = {
    title: plantName || "발전소",
    currentOutput: 1005.4,
    currentPower: 412,
    monthPower: 8.7,
    yearPower: 96.4,
  };

  return (
    <div className="sidebar-content plant-sidebar-content">
      <section className="sidebar-panel turbine-power-panel">
        <SideTitle>
          {plantName} 현황
        </SideTitle>

        <PlantSummaryGroup
          weatherInfo={weatherInfo}
          powerInfo={powerInfo}
        />
      </section>

      <div className="sidebar-divider-wrap plant-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel plant-turbine-panel">
        <SideTitle>터빈 현황</SideTitle>
        <TurbineList
          items={turbineItems}
          onSelectTurbine={onSelectTurbine}
        />
      </section>

      <div className="sidebar-divider-wrap plant-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel plant-report-panel">
        <ReportTitleToggle
          selectedType={reportMode}
          onChange={setReportMode}
          firstType="operation"
          secondType="inspection"
          firstTitle="발전소 운영 보고서"
          secondTitle="점검 보고서"
        />

        {reportMode === "operation" && (
          <ReportBox
            reportMode={reportMode}
            startDate={today}
            endDate={today}
            onCreateReport={(reportData) => {
              console.log("발전소 운영 보고서 생성 데이터:", {
                ...reportData,
                plantId: selectedPlant?.id,
                plantName,
              });
            }}
          />
        )}

        {reportMode === "inspection" && (
          <InspectionReportBox
            turbineOptions={turbineItems.map((turbine) => turbine.name)}
            onCreateReport={(reportData) => {
              onCreateInspectionReport?.({
                ...reportData,
                plantId: selectedPlant?.id,
                plantName,
              });
            }}
          />
        )}
      </section>
    </div>
  );
}

export default PlantSideBar;