import { useState } from "react";

import WeatherGroup from "./Weather/WeatherGroup";
import TurbineList from "./Turbine/TurbineList";
import ReportBox from "./Report/ReportBox";
import SideTitle from "./SideTitle";
import ReportTitleToggle from "./Report/ReportTitleToggle";
import InspectionReportBox from "./Report/InspectionReportBox";

import { WEATHER_TYPE } from "./Weather/WeatherItem";
import { TURBINE_STATUS } from "./Turbine/TurbineItem";
import "./PlantSideBar.css";

function PlantSideBar({
  selectedPlant,
  onSelectTurbine,
  onCreateInspectionReport,
}) {
  const [reportMode, setReportMode] = useState("operation");

  const plantName = selectedPlant?.title || selectedPlant?.name;
  const today = new Date().toISOString().slice(0, 10);

  const weatherItems = [
    {
      id: `${selectedPlant?.id || 1}-now`,
      title: "현재 날씨",
      weatherType: WEATHER_TYPE.RAIN,
      temperature: 32.0,
      windSpeed: 5.0,
    },
    {
      id: `${selectedPlant?.id || 1}-6h`,
      title: "오후 4시",
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 28.0,
      windSpeed: 10.0,
    },
    {
      id: `${selectedPlant?.id || 1}-12h`,
      title: "오후 10시",
      weatherType: WEATHER_TYPE.PARTLY_CLOUDY,
      temperature: 30.0,
      windSpeed: 5.0,
    },
  ];

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

  return (
    <div className="sidebar-content plant-sidebar-content">
      <section className="sidebar-panel plant-weather-panel">
        <SideTitle>{plantName} 날씨</SideTitle>
        <WeatherGroup items={weatherItems} />
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