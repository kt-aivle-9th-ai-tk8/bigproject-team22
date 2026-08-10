import { useState } from "react";

import WeatherGroup from "./Weather/WeatherGroup";
import PowerGroup from "./Power/PowerGroup";
import FaultList from "./Fault/FaultList";
import SideTitle from "./SideTitle";
import WeatherHelpButton from "./Weather/WeatherHelpButton";
import WeatherHelpPopup from "./Weather/WeatherHelpPopup";

import "./MapSideBar.css";

function MapSideBar({
  plants = [],
  notifications = [],
  onSelectPlant,
}) {
  const [isWeatherHelpOpen, setIsWeatherHelpOpen] = useState(false);
  /*
   * 발전소 데이터를 날씨 컴포넌트 형태로 변환
   *
   * plant 기본 데이터를 함께 넣었기 때문에
   * 클릭 시 전체 발전소 객체를 사용할 수 있다.
   */
  const weatherItems = plants
    .filter((plant) => plant.weather)
    .map((plant) => ({
      ...plant,

      title: plant.name,

      weatherType:
        plant.weather.weatherType,

      temperature:
        plant.weather.temperature,

      windSpeed:
        plant.weather.windSpeed,
    })).slice(0, 3);

  /*
   * 발전소 데이터를 발전량 컴포넌트 형태로 변환
   */
  const powerItems = plants
    .filter((plant) => plant.power)
    .map((plant) => ({
      ...plant,

      title: plant.name,

      currentOutput:
        plant.power.currentOutput,

      currentPower:
        plant.power.currentPower,

      monthPower:
        plant.power.monthPower,

      yearPower:
        plant.power.yearPower,
    })).slice(0, 3);

  /*
   * 각 발전소의 faults 배열을 하나의 결함 목록으로 합침
   */
  const faultItems = plants.flatMap(
    (plant) => {
      return (plant.faults || []).map(
        (fault) => ({
          ...fault,

          plantId: plant.id,
          plantName: plant.name,

          /*
           * 필요한 경우 결함 클릭 시에도
           * 발전소 전체 정보를 사용할 수 있음
           */
          plantData: plant,
        })
      );
    }
  );

  return (
    <div className="sidebar-content map-sidebar-content">
      <section className="sidebar-panel map-weather-panel">
        <SideTitle
          leftContent={
            <WeatherHelpButton
              isOpen={isWeatherHelpOpen}
              onToggle={() => setIsWeatherHelpOpen((prev) => !prev)}
              onClose={() => setIsWeatherHelpOpen(false)}
            />
          }
        >
          주요 발전소 날씨
        </SideTitle>

        <WeatherGroup
          items={weatherItems}
          onSelectPlant={onSelectPlant}
        />
      </section>

      <div className="sidebar-divider-wrap map-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel map-power-panel">
        <SideTitle>
          주요 발전소 발전량
        </SideTitle>

        <PowerGroup
          items={powerItems}
          onSelectPlant={onSelectPlant}
        />
      </section>

      <div className="sidebar-divider-wrap map-divider">
        <div className="sidebar-divider" />
      </div>

      <section className="sidebar-panel map-fault-panel">
        <SideTitle>
          실시간 점검 알림
        </SideTitle>

        <FaultList items={notifications} />
      </section>
    </div>
  );
}

export default MapSideBar;