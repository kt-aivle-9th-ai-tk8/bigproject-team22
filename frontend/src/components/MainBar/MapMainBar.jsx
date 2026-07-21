import { useState } from "react";

import Map from "./Map/Map";
import "./MapMainBar.css";
import plantIcon from "../../assets/icon/plant.png";

function MapMainBar({
  plants = [],
  isLoading,
  error,
  onSelectPlant,
}) {
  const [isPlantListOpen, setIsPlantListOpen] = useState(false);

  const handleTogglePlantList = () => {
    setIsPlantListOpen((prev) => !prev);
  };

  const handleSelectPlant = (plant) => {
    onSelectPlant?.(plant);
    setIsPlantListOpen(false);
  };

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

      <button
        className={`map-plant-menu-button ${isPlantListOpen ? "active" : ""}`}
        type="button"
        aria-label="발전소 리스트 열기"
        onClick={handleTogglePlantList}
      >
        <span />
        <span />
        <span />
      </button>

      {isPlantListOpen && (
        <div className="map-plant-list-panel">
          <div className="map-plant-list-header">
            <strong>발전소 리스트</strong>
            <button
              type="button"
              onClick={() => setIsPlantListOpen(false)}
            >
              닫기
            </button>
          </div>

          <div className="map-plant-list">
            {plants.length === 0 ? (
              <div className="map-plant-empty">
                표시할 발전소가 없습니다.
              </div>
            ) : (
              plants.map((plant) => {
                const plantName =
                  plant.title ||
                  plant.name ||
                  plant.plantName ||
                  `발전소 ${plant.id}`;

                return (
                  <button
                    className="map-plant-list-item"
                    type="button"
                    key={plant.id}
                    onClick={() => handleSelectPlant(plant)}
                  >
                    <span className="map-plant-list-name">
                      {plantName}
                    </span>

                    {(plant.address || plant.location) && (
                      <span className="map-plant-list-location">
                        {plant.address || plant.location}
                      </span>
                    )}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default MapMainBar;