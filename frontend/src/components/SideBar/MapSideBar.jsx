import WeatherGroup from "./WeatherGroup";
import PowerGroup from "./PowerGroup";
import SideTitle from "./SideTitle";

import { WEATHER_TYPE } from "./WeatherItem";

function MapSideBar() {
  const weatherItems = [
    {
      title: "장흥 발전소",
      weatherType: WEATHER_TYPE.RAIN,
      temperature: 32.0,
      windSpeed: 5.0,
    },
    {
      title: "경주 발전소",
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 28.0,
      windSpeed: 10.0,
    },
    {
      title: "대구 발전소",
      weatherType: WEATHER_TYPE.PARTLY_CLOUDY,
      temperature: 30.0,
      windSpeed: 5.0,
    },
  ];

  const powerItems = [
    {
      title: "장흥 발전소",
      currentOutput: 18,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
    {
      title: "경주 발전소",
      currentOutput: 19.4,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
    {
      title: "대구 발전소",
      currentOutput: 40,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },
  ];

  return (
    <div className="sidebar-content">
      <div className="sidebar-content">
        <section className="sidebar-panel">
          <SideTitle>주요 발전소 날씨</SideTitle>
          <WeatherGroup items={weatherItems} />
        </section>

        <div className="sidebar-divider-wrap">
          <div className="sidebar-divider" />
        </div>

        <section className="sidebar-panel">
          <SideTitle>주요 발전소 발전량</SideTitle>
          <PowerGroup items={powerItems} />
        </section>
      </div>
    </div>
  );
}

export default MapSideBar;