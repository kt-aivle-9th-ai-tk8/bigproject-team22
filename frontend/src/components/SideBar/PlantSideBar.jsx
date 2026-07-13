import WeatherGroup from "./Weather/WeatherGroup";
import TurbineList from "./Turbine/TurbineList";
import ReportBox from "./Report/ReportBox";
import SideTitle from "./SideTitle";

import { WEATHER_TYPE } from "./Weather/WeatherItem";
import { TURBINE_STATUS } from "./Turbine/TurbineItem";

import "./PlantSideBar.css";

function PlantSideBar({ selectedPlant, onSelectTurbine }) {
  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";

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
    {
      id: 4,
      name: "터빈 D",
      status: TURBINE_STATUS.NORMAL,
      alertCount: 0,
      hasEmergency: false,
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
        <SideTitle>발전소 보고서 작성</SideTitle>
        <ReportBox
          startDate={today}
          endDate={today}
        />
      </section>
    </div>
  );
}

export default PlantSideBar;