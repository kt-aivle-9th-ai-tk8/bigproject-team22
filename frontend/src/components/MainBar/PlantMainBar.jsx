import { memo } from "react";

import "./PlantMainBar.css";

import turbineIcon from "../../assets/icon/blade.png";
import Map from "./Map/Map";

function PlantMainBar({
  turbines = [],
  isLoading = false,
  error = null,
  onSelectTurbine,
}) {

  const [isTurbineListOpen, setIsTurbineListOpen] = useState(false);
  
    const handleToggleTurbineList = () => {
      setIsTurbineListOpen((prev) => !prev);
    };
  
    const handleSelectTurbine = (turbine) => {
      onSelectTurbine?.(turbine);
      setIsTurbineListOpen(false);
    };

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

      <button
        className={`map-plant-menu-button ${isPlantListOpen ? "active" : ""}`}
        type="button"
        aria-label="터빈 리스트 열기"
        onClick={handleToggleTurbineList}
      >
        <span />
        <span />
        <span />
      </button>

      {isTurbineListOpen && (
        <div className="map-plant-list-panel">
          <div className="map-plant-list-header">
            <strong>터빈 리스트</strong>
            <button
              type="button"
              onClick={() => setIsTurbineListOpen(false)}
            >
              닫기
            </button>
          </div>

          <div className="map-plant-list">
            {turbines.length === 0 ? (
              <div className="map-plant-empty">
                표시할 터빈이 없습니다.
              </div>
            ) : (
              turbines.map((turbine) => {
                const turbineName =
                  turbines.name ||
                  `터빈 ${turbines.id}`;

                return (
                  <button
                    className="map-plant-list-item"
                    type="button"
                    key={turbines.id}
                    onClick={() => handleSelectTurbine(turbine)}
                  >
                    <span className="map-plant-list-name">
                      {turbineName}
                    </span>

                    {(turbine.address || turbine.location) && (
                      <span className="map-plant-list-location">
                        {turbine.address || turbine.location}
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

const areEqual = (prevProps, nextProps) => {
  return (
    prevProps.turbines === nextProps.turbines &&
    prevProps.isLoading === nextProps.isLoading &&
    prevProps.error === nextProps.error
  );
};

export default memo(PlantMainBar, areEqual);