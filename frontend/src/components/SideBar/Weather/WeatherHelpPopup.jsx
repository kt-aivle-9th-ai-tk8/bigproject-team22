import {
  WEATHER_TYPE,
  WEATHER_EMOJI_MAP,
  WEATHER_LABEL_MAP,
  WEATHER_DESCRIPTION_MAP,
} from "./WeatherItem";

import "./WeatherHelpPopup.css";

const WEATHER_HELP_ITEMS = [
  WEATHER_TYPE.STORM,
  WEATHER_TYPE.BLIZZARD,
  WEATHER_TYPE.THUNDERSTORM,
  WEATHER_TYPE.FREEZING_RAIN,
  WEATHER_TYPE.HAIL,
  WEATHER_TYPE.ICING,
  WEATHER_TYPE.SLEET,
  WEATHER_TYPE.SNOW,
  WEATHER_TYPE.SHOWER,
  WEATHER_TYPE.RAIN,
  WEATHER_TYPE.FOG,
  WEATHER_TYPE.CLOUDY,
  WEATHER_TYPE.CLEAR,
];

function WeatherHelpPopup({
  onClose,
}) {
  return (
    <div className="weather-help-popup">
    <div className="weather-help-popup-header">
        <div className="weather-help-popup-title">
        날씨 아이콘 설명
        </div>

        <button
        className="weather-help-popup-close-button"
        type="button"
        onClick={onClose}
        aria-label="닫기"
        >
        ×
        </button>
    </div>

    <div className="weather-help-list">
        {WEATHER_HELP_ITEMS.map((weatherType) => (
        <div
            className="weather-help-row"
            key={weatherType}
        >
            <div className="weather-help-emoji">
            {WEATHER_EMOJI_MAP[weatherType]}
            </div>

            <div className="weather-help-content">
            <div className="weather-help-label">
                {WEATHER_LABEL_MAP[weatherType]}
            </div>

            <div className="weather-help-description">
                {WEATHER_DESCRIPTION_MAP[weatherType]}
            </div>
            </div>
        </div>
        ))}
    </div>
    </div>
  );
}

export default WeatherHelpPopup;