function Turbine3DSidebar({
  weather,
  onChangeWeather,
  bladeSpeed,
  onChangeBladeSpeed,
  isRunning,
  onToggleRunning,
}) {
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
        <label>회전 속도</label>
        <input
          type="range"
          min="0"
          max="0.08"
          step="0.005"
          value={bladeSpeed}
          onChange={(event) => onChangeBladeSpeed(Number(event.target.value))}
        />
        <span>{bladeSpeed.toFixed(3)}</span>
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