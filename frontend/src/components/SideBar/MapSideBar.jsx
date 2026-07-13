import WeatherGroup from "./WeatherGroup";
import { WEATHER_TYPE } from "./WeatherItem";
import SideTitle from "./SideTitle";

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

  return (
    <div className="sidebar-content">
      <section className="sidebar-panel">
        <SideTitle>주요 발전소 날씨</SideTitle>

        <WeatherGroup items={weatherItems} />
      </section>
    </div>
  );
}

export default MapSideBar;