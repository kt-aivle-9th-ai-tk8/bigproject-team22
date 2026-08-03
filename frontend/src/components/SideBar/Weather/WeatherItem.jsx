import "./WeatherItem.css";

export const WEATHER_TYPE = {
  STORM: "STORM",
  BLIZZARD: "BLIZZARD",
  THUNDERSTORM: "THUNDERSTORM",
  FREEZING_RAIN: "FREEZING_RAIN",
  HAIL: "HAIL",
  ICING: "ICING",
  SLEET: "SLEET",
  SNOW: "SNOW",
  SHOWER: "SHOWER",
  RAIN: "RAIN",
  FOG: "FOG",
  CLOUDY: "CLOUDY",
  CLEAR: "CLEAR",
};

export const WEATHER_EMOJI_MAP = {
  [WEATHER_TYPE.STORM]: "🌪️",
  [WEATHER_TYPE.BLIZZARD]: "🌨️💨",
  [WEATHER_TYPE.THUNDERSTORM]: "⛈️",
  [WEATHER_TYPE.FREEZING_RAIN]: "🌧️❄️",
  [WEATHER_TYPE.HAIL]: "🌩️🧊",
  [WEATHER_TYPE.ICING]: "🧊",
  [WEATHER_TYPE.SLEET]: "🌨️🌧️",
  [WEATHER_TYPE.SNOW]: "🌨️",
  [WEATHER_TYPE.SHOWER]: "🌦️",
  [WEATHER_TYPE.RAIN]: "🌧️",
  [WEATHER_TYPE.FOG]: "🌫️",
  [WEATHER_TYPE.CLOUDY]: "☁️",
  [WEATHER_TYPE.CLEAR]: "☀️",
};

export const WEATHER_LABEL_MAP = {
  [WEATHER_TYPE.STORM]: "폭풍",
  [WEATHER_TYPE.BLIZZARD]: "눈보라",
  [WEATHER_TYPE.THUNDERSTORM]: "뇌우",
  [WEATHER_TYPE.FREEZING_RAIN]: "어는 비",
  [WEATHER_TYPE.HAIL]: "우박",
  [WEATHER_TYPE.ICING]: "결빙",
  [WEATHER_TYPE.SLEET]: "진눈깨비",
  [WEATHER_TYPE.SNOW]: "눈",
  [WEATHER_TYPE.SHOWER]: "소나기",
  [WEATHER_TYPE.RAIN]: "비",
  [WEATHER_TYPE.FOG]: "안개·연무",
  [WEATHER_TYPE.CLOUDY]: "흐림",
  [WEATHER_TYPE.CLEAR]: "맑음",
};

export const WEATHER_DESCRIPTION_MAP = {
  [WEATHER_TYPE.STORM]: "폭풍·용오름·회오리바람",
  [WEATHER_TYPE.BLIZZARD]: "강한 바람을 동반한 눈보라",
  [WEATHER_TYPE.THUNDERSTORM]: "뇌전·천둥·번개를 동반한 기상현상",
  [WEATHER_TYPE.FREEZING_RAIN]: "지면이나 물체에 얼어붙는 비·이슬비",
  [WEATHER_TYPE.HAIL]: "싸락우박·우박·얼음침",
  [WEATHER_TYPE.ICING]: "이슬·서리·착빙·결빙·해빙 현상",
  [WEATHER_TYPE.SLEET]: "비와 눈이 섞여 내리는 현상",
  [WEATHER_TYPE.SNOW]: "눈·소낙눈·싸락눈·날린눈·적설",
  [WEATHER_TYPE.SHOWER]: "갑자기 내리는 소나기 또는 부분강수",
  [WEATHER_TYPE.RAIN]: "비 또는 이슬비",
  [WEATHER_TYPE.FOG]: "안개·연무·황사·연기·먼지 현상",
  [WEATHER_TYPE.CLOUDY]: "일조가 적거나 하늘이 흐린 상태",
  [WEATHER_TYPE.CLEAR]: "맑음·갬 또는 대기 중 빛현상",
};


function WeatherItem({
  title,
  weatherType = WEATHER_TYPE.RAIN,
  temperature = 32.0,
  windSpeed = 5.0,
  onClick,
}) {
  const weatherEmoji =
    WEATHER_EMOJI_MAP[weatherType] ||
    WEATHER_EMOJI_MAP[WEATHER_TYPE.RAIN];

  const weatherLabel =
    WEATHER_LABEL_MAP[weatherType] ||
    WEATHER_LABEL_MAP[WEATHER_TYPE.RAIN];

  const isMultipleEmoji = [...weatherEmoji].length > 2;
  return (
    <div
      className={`weather-item ${onClick ? "weather-item-clickable" : ""}`}
      onClick={onClick}
    >
      {title && (
        <div className="weather-item-title">
          {title}
        </div>
      )}

      <div
        className={`weather-item-emoji ${
          isMultipleEmoji ? "weather-item-emoji-multiple" : ""
        }`}
        aria-label={weatherLabel}
        title={weatherLabel}
      >
        {weatherEmoji}
      </div>

      <div className="weather-item-temperature">
        {temperature.toFixed(1)}°
      </div>

      <div className="weather-item-wind">
        <span
          className="weather-item-wind-emoji"
          aria-label="wind"
          title="wind"
        >
          💨
        </span>

        <span>{windSpeed.toFixed(1)} m/s</span>
      </div>
    </div>
  );
}

export default WeatherItem;