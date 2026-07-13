import WeatherGroup from "./Weather/WeatherGroup";
import PowerGroup from "./Power/PowerGroup";
import FaultList from "./Fault/FaultList";
import SideTitle from "./SideTitle";

import { WEATHER_TYPE } from "./Weather/WeatherItem";
import { FAULT_STATUS } from "./Fault/FaultItem";

import "./MapSideBar.css";

function MapSideBar({ onSelectPlant }) {
  const weatherItems = [
    {
      id: 1,
      title: "장흥 발전소",
      weatherType: WEATHER_TYPE.RAIN,
      temperature: 32.0,
      windSpeed: 5.0,
    },
    {
      id: 2,
      title: "경주 발전소",
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 28.0,
      windSpeed: 10.0,
    },
    {
      id: 3,
      title: "대구 발전소",
      weatherType: WEATHER_TYPE.PARTLY_CLOUDY,
      temperature: 30.0,
      windSpeed: 5.0,
    },
  ];

  const powerItems = [
    {
      id: 1,
      title: "장흥 발전소",
      currentOutput: 18.4,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
    {
      id: 2,
      title: "경주 발전소",
      currentOutput: 19,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
    {
      id: 3,
      title: "대구 발전소",
      currentOutput: 40,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
  ];

  const faultItems = [
    {
      plantName: "장흥 발전소",
      date: "07.08",
      time: "12:00",
      status: FAULT_STATUS.ALERT,
    },
    {
      plantName: "대구 발전소",
      date: "07.08",
      time: "11:00",
      status: FAULT_STATUS.WARNING,
    },
  ];

  return (
    <div className="sidebar-content map-sidebar-content">
      <section className="sidebar-panel map-weather-panel">
        <SideTitle>주요 발전소 날씨</SideTitle>
        <WeatherGroup
          items={weatherItems}
          onSelectPlant={onSelectPlant}
        />
      </section>

      <div className="sidebar-divider-wrap map-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel map-power-panel">
        <SideTitle>주요 발전소 발전량</SideTitle>
        <PowerGroup
          items={powerItems}
          onSelectPlant={onSelectPlant}
        />
      </section>

      <div className="sidebar-divider-wrap map-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel map-fault-panel">
        <SideTitle>실시간 결함 내역</SideTitle>
        <FaultList items={faultItems} />
      </section>
    </div>
  );
}

export default MapSideBar;