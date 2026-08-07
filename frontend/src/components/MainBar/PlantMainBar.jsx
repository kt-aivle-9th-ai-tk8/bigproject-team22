import "./PlantMainBar.css";

import turbineIcon from "../../assets/icon/blade.png";
import Map from "./Map/Map";

function PlantMainBar({
  turbines = [],
  isLoading = false,
  error = null,
  onSelectTurbine,
}) {
  if (isLoading) {
    return (
      <div className="map-main-bar">
        터빈 데이터를 불러오는 중입니다.
      </div>
    );
  }

  if (error) {
    return (
      <div className="map-main-bar">
        터빈 데이터를 불러오지 못했습니다.
      </div>
    );
  }

  if (turbines.length === 0) {
    return (
      <div className="map-main-bar">
        등록된 터빈이 없습니다.
      </div>
    );
  }
  console.log("터빈 리스트 ", turbines);

  return (
    <div className="map-main-bar">
      <Map
        objects={turbines}
        iconSrc={turbineIcon}
        iconScale={0.07}
        clusterDistance={40}
        clusterMinDistance={15}
        onSelectObject={onSelectTurbine}
      />
    </div>
  );
}

export default PlantMainBar;