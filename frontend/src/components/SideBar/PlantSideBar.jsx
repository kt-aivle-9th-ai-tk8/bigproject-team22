import WeatherGroup from "./WeatherGroup";
import SideTitle from "./SideTitle";

import { WEATHER_TYPE } from "./WeatherItem";

function PlantSideBar({ selectedPlant }) {
  const plantName = selectedPlant?.title || selectedPlant?.name || "장흥 발전소";

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
      title: "6시간 뒤 날씨",
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 28.0,
      windSpeed: 10.0,
    },
    {
      id: `${selectedPlant?.id || 1}-12h`,
      title: "12시간 뒤 날씨",
      weatherType: WEATHER_TYPE.PARTLY_CLOUDY,
      temperature: 30.0,
      windSpeed: 5.0,
    },
  ];

  return (
    <div className="sidebar-content">
      <section className="sidebar-panel">
        <SideTitle>{plantName} 날씨</SideTitle>
        <WeatherGroup items={weatherItems} />
      </section>
    </div>
  );
}

export default PlantSideBar;