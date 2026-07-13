import "./WeatherItem.css";

import rainIcon from "../../assets/weather/rain.png";
import cloudyIcon from "../../assets/weather/cloudy.png";
import partlyCloudyIcon from "../../assets/weather/partly-cloudy.png";
import sunnyIcon from "../../assets/weather/sunny.png";
import windyIcon from "../../assets/icon/windy.png";

export const WEATHER_TYPE = {
  RAIN: "RAIN",
  CLOUDY: "CLOUDY",
  PARTLY_CLOUDY: "PARTLY_CLOUDY",
  SUNNY: "SUNNY",
};

const WEATHER_ICON_MAP = {
  [WEATHER_TYPE.RAIN]: rainIcon,
  [WEATHER_TYPE.CLOUDY]: cloudyIcon,
  [WEATHER_TYPE.PARTLY_CLOUDY]: partlyCloudyIcon,
  [WEATHER_TYPE.SUNNY]: sunnyIcon,
};

function WeatherItem({
  title,
  weatherType = WEATHER_TYPE.RAIN,
  temperature = 32.0,
  windSpeed = 5.0,
}) {
  const weatherIcon = WEATHER_ICON_MAP[weatherType] || rainIcon;

  return (
    <div className="weather-item">
      {title && <div className="weather-item-title">{title}</div>}

      <img
        className="weather-item-icon"
        src={weatherIcon}
        alt={weatherType}
      />

      <div className="weather-item-temperature">
        {temperature.toFixed(1)}°
      </div>

      <div className="weather-item-wind">
        <img
          className="weather-item-wind-icon"
          src={windyIcon}
          alt="wind"
        />
        <span>{windSpeed.toFixed(1)} m/s</span>
      </div>
    </div>
  );
}

export default WeatherItem;