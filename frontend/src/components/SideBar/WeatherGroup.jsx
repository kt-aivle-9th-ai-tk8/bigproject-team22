import WeatherItem from "./WeatherItem";
import "./WeatherGroup.css";

function WeatherGroup({ items, onSelectPlant }) {
  return (
    <div
      className="weather-group"
      style={{ "--weather-count": items.length }}
    >
      {items.map((item, index) => (
        <div className="weather-group-item" key={item.id || item.title}>
          <WeatherItem
            title={item.title}
            weatherType={item.weatherType}
            temperature={item.temperature}
            windSpeed={item.windSpeed}
            onClick={onSelectPlant ? () => onSelectPlant(item) : undefined}
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