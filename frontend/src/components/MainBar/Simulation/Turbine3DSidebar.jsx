import "./Turbine3DSidebar.css";

const STATUS_TEXT = {
  "manual-stop": "수동 정지",
  "below-cut-in": "시동 풍속 미달",
  normal: "정상 운전",
  rated: "정격 운전",
  "cut-out": "강풍 안전 정지",
};

function Turbine3DSidebar({
  weather,
  onChangeWeather,
  windSpeed,
  onChangeWindSpeed,
  bladeSpeed,
  status,
  isRunning,
  onToggleRunning,
  onRunSimulation,
}) {
  const statusText = STATUS_TEXT[status] ?? "상태 확인 중";

  const handleWindSpeedChange = (event) => {
    onChangeWindSpeed(Number(event.target.value));
  };

  return (
    <div className="turbine-3d-panel">
      <h3>3D 시뮬레이션 제어</h3>

      <div className="turbine-3d-field">
        <label>날씨</label>

        <div className="turbine-weather-button-row">
          <button
            type="button"
            className={weather === "sunny" ? "active" : ""}
            onClick={() => onChangeWeather("sunny")}
          >
            맑음
          </button>

          <button
            type="button"
            className={weather === "cloudy" ? "active" : ""}
            onClick={() => onChangeWeather("cloudy")}
          >
            흐림
          </button>

          <button
            type="button"
            className={weather === "rainy" ? "active" : ""}
            onClick={() => onChangeWeather("rainy")}
          >
            비
          </button>
        </div>
      </div>

      <div className="turbine-3d-field">
        <label htmlFor="wind-speed-number">
          풍속 입력
        </label>

        <div className="wind-speed-input-wrap">
          <input
            id="wind-speed-number"
            type="number"
            min="0"
            max="30"
            step="0.5"
            value={windSpeed}
            onChange={handleWindSpeedChange}
          />

          <span className="wind-speed-unit">
            m/s
          </span>
        </div>
      </div>

      <div className="turbine-3d-status">
        <div>
          <span>운전 상태: </span>
          <strong>{statusText}</strong>
        </div>
      </div>

      <div className="turbine-3d-wind-guide">
        <p>시동 풍속: 3 m/s</p>
        <p>정격 풍속: 12 m/s</p>
        <p>차단 풍속: 25 m/s</p>
      </div>

      <button
        className="turbine-3d-toggle-button"
        type="button"
        onClick={onToggleRunning}
      >
        {isRunning ? "정지" : "가동"}
      </button>
    </div>
  );
}

export default Turbine3DSidebar;