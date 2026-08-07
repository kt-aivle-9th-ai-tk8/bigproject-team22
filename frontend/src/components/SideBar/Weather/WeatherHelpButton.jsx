import WeatherHelpPopup from "./WeatherHelpPopup";
import "./WeatherHelpButton.css";

function WeatherHelpButton({
  isOpen,
  onToggle,
  onClose,
}) {
  return (
    <div className="weather-help-wrap">
      <button
        className="weather-help-button"
        type="button"
        onClick={onToggle}
        aria-label="날씨 아이콘 도움말"
        title="날씨 아이콘 도움말"
      >
        ?
      </button>

      {isOpen && (
        <WeatherHelpPopup
          onClose={onClose}
        />
      )}
    </div>
  );
}

export default WeatherHelpButton;