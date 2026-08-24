import WeatherItem from "./Weather/WeatherItem";
import PowerGauge from "./Power/PowerGauge";
import "./PlantSummaryGroup.css";

function PlantSummaryGroup({
  weatherInfo,
  powerInfo,
}) {
  const formatPower = (value) => {
    const numberValue = Number(value) || 0;

    if (numberValue >= 1000) {
      return `${(numberValue / 1000).toFixed(2)} MWh`;
    }

    return `${numberValue.toFixed(2)} kWh`;
  };

  return (
    <div className="plant-summary-group">
      <div className="plant-summary-item">
        <div className="plant-summary-column">
          <div className="plant-summary-section-label">
            현재 날씨
          </div>

          <WeatherItem
            weatherType={weatherInfo.weatherType}
            temperature={weatherInfo.temperature}
            windSpeed={weatherInfo.windSpeed}
          />
        </div>
      </div>

      <div className="plant-summary-divider" />

      <div className="plant-summary-item">
        <div className="plant-summary-column">
          <div className="plant-summary-section-label">
            현재 발전량
          </div>

          <PowerGauge value={powerInfo.currentOutput} />

          <div className="plant-summary-power-output-value">
            {formatPower(powerInfo.currentOutput)}
          </div>
        </div>
      </div>

      <div className="plant-summary-divider" />

      <div className="plant-summary-item">
        <div className="plant-summary-column plant-summary-generation-column">
          <div className="plant-summary-section-label">
            발전량 통계
          </div>

          <div className="plant-summary-generation-list">
            <div className="plant-summary-power-data">
              <div className="plant-summary-power-label">
                금일 발전량
              </div>
              <div className="plant-summary-power-value">
                {formatPower(powerInfo.currentPower)}
              </div>
            </div>

            <div className="plant-summary-power-data">
              <div className="plant-summary-power-label">
                이번 달 발전량
              </div>
              <div className="plant-summary-power-value">
                {formatPower(powerInfo.monthPower)}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PlantSummaryGroup;