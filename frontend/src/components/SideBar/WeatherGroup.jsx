import WeatherItem from "./WeatherItem";
import "./WeatherGroup.css";

function WeatherGroup({ items }) {
  return (
    <div
      className="weather-group"
      style={{ "--weather-count": items.length }}
    >
      {items.map((item, index) => (
        <div className="weather-group-item" key={item.title}>
          <WeatherItem
            title={item.title}
            weatherType={item.weatherType}
            temperature={item.temperature}
            windSpeed={item.windSpeed}
          />

          {index !== items.length - 1 && (
            <div className="weather-divider" />
          )}
        </div>
      ))}
    </div>
  );
}

export default WeatherGroup;