import WeatherItem from "./Weather/WeatherItem";
import PowerGauge from "./Power/PowerGauge";
import "./PlantSummaryGroup.css";

function PlantSummaryGroup({
  weatherInfo,
  powerInfo,
}) {
  return (
    <div className="plant-summary-group">
        <div className="plant-summary-item">
            <div className="plant-summary-power">
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
        <div className="plant-summary-power">
            <div className="plant-summary-section-label">
                현재 출력
            </div>

          <PowerGauge value={powerInfo.currentOutput} />

          <div className="plant-summary-power-output-value">
            {powerInfo.currentOutput.toFixed(1)} MWh
          </div>

          <div className="plant-summary-power-data">
            <div className="plant-summary-power-label">
              금일 발전량
            </div>

            <div className="plant-summary-power-value">
              {powerInfo.currentPower} MWh
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PlantSummaryGroup;