import { useState } from "react";

import TurbineList from "./Turbine/TurbineList";
import PlantSummaryGroup from "./PlantSummaryGroup";
import ReportBox from "./Report/ReportBox";
import SideTitle from "./SideTitle";
import ReportTitleToggle from "./Report/ReportTitleToggle";
import InspectionReportBox from "./Report/InspectionReportBox";
import WeatherHelpButton from "./Weather/WeatherHelpButton";
import WeatherHelpPopup from "./Weather/WeatherHelpPopup";

import { TURBINE_STATUS } from "./Turbine/TurbineItem";
import { WEATHER_TYPE } from "./Weather/WeatherItem";

import "./PlantSideBar.css";

function PlantSideBar({
  selectedPlant,
  windFarmDetail,
  onSelectTurbine,
  onCreateInspectionReport,
}) {
  const [isWeatherHelpOpen, setIsWeatherHelpOpen] = useState(false);
  const [reportMode, setReportMode] = useState("operation");

  const plantName = selectedPlant?.title || selectedPlant?.name;
  const today = new Date().toISOString().slice(0, 10);

  const turbineItems = (windFarmDetail?.turbines || []).map(
    (turbine) => {
      let status = TURBINE_STATUS.NORMAL;

      if (turbine.currentPower === null) {
        status = TURBINE_STATUS.NO_DATA;
      } else if (turbine.currentPower <= 0) {
        status = TURBINE_STATUS.ZERO_POWER;
      }

      return {
        id: turbine.id,
        name: turbine.name,
        status,
        abnormalDetected: false,
      };
    }
  );
  const weatherInfo = {
    title: plantName || "발전소",
    weatherType: windFarmDetail?.weather?.weatherType,
    temperature: windFarmDetail?.weather?.temperature ?? 0,
    windSpeed: windFarmDetail?.weather?.windSpeed ?? 0,
  };

  const powerInfo = {
    title: plantName || "발전소",
    currentOutput: windFarmDetail?.power?.currentOutput ?? 0,
    currentPower: windFarmDetail?.power?.currentPower ?? 0,
    monthPower: windFarmDetail?.power?.monthPower ?? 0,
    yearPower: windFarmDetail?.power?.yearPower ?? 0,
  };

  return (
    <div className="sidebar-content plant-sidebar-content">
      <section className="sidebar-panel turbine-power-panel">
        <SideTitle
          leftContent={
            <WeatherHelpButton
              isOpen={isWeatherHelpOpen}
              onToggle={() => setIsWeatherHelpOpen((prev) => !prev)}
              onClose={() => setIsWeatherHelpOpen(false)}
            />
          }>
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