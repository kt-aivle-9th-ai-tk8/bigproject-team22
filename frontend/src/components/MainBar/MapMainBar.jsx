import Map from "./Map/Map";
import "./MapMainBar.css";
import plantIcon from "../../assets/icon/plant.png";

function MapMainBar({
  plants = [],
  isLoading,
  error,
  onSelectPlant,
}) {
  if (isLoading) {
    return (
      <div className="map-main-bar">
        발전소 데이터를 불러오는 중입니다.
      </div>
    );
  }

  if (error) {
    return (
      <div className="map-main-bar">
        발전소 데이터를 불러오지 못했습니다.
      </div>
    );
  }

  return (
    <div className="map-main-bar">
      <Map
        objects={plants}
        iconSrc={plantIcon}
        iconScale={0.07}
        clusterDistance={40}
        clusterMinDistance={15}
        onSelectObject={onSelectPlant}
      />
    </div>
  );
}

export default MapMainBar;