import Map from "./Map/Map";
import "./MapMainBar.css";

function MapMainBar({ onSelectPlant }) {
  const plants = [
    {
      id: 1,
      name: "장흥 발전소",
      coordinate: [126.907, 34.681],
    },
    {
      id: 2,
      name: "경주 발전소",
      coordinate: [129.2247, 35.8562],
    },
    {
      id: 3,
      name: "대구 발전소",
      coordinate: [128.6014, 35.8714],
    },
  ];

  return (
    <div className="map-main-bar">
      <Map
        plants={plants}
        onSelectPlant={onSelectPlant}
      />
    </div>
  );
}

export default MapMainBar;