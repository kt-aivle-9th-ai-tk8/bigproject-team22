import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";

import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import { WEATHER_TYPE } from "../components/SideBar/Weather/WeatherItem";
import { FAULT_STATUS } from "../components/SideBar/Fault/FaultItem";
import { TURBINE_STATUS } from "../components/SideBar/Turbine/TurbineItem";

import "./MainScreen.css";
import "../components/Bar.css";

/*
 * 발전소 기본 정보, 지도 좌표, 날씨, 발전량,
 * 결함 데이터를 하나의 배열에서 관리
 */
const dummyPlants = [
  {
    id: 1,
    name: "장흥 발전소",
    coordinate: [126.907, 34.681],

    weather: {
      weatherType: WEATHER_TYPE.RAIN,
      temperature: 32.0,
      windSpeed: 5.0,
    },

    power: {
      currentOutput: 18.4,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },

    faults: [
      {
        id: 1,
        date: "07.08",
        time: "12:00",
        status: FAULT_STATUS.ALERT,
      },
    ],

    turbines: [
      {
        id: 1,
        name: "터빈 A",
        status: TURBINE_STATUS.ALERT,
        alertCount: 6,
        hasEmergency: true,
        coordinate: [126.9064, 34.6815],
      },
      {
        id: 2,
        name: "터빈 B",
        status: TURBINE_STATUS.WARNING,
        alertCount: 3,
        hasEmergency: false,
        coordinate: [126.9077, 34.6816],
      },
      {
        id: 3,
        name: "터빈 C",
        status: TURBINE_STATUS.ALERT,
        alertCount: 2,
        hasEmergency: true,
        coordinate: [126.9066, 34.6804],
      },
      {
        id: 4,
        name: "터빈 D",
        status: TURBINE_STATUS.NORMAL,
        alertCount: 0,
        hasEmergency: false,
        coordinate: [126.9078, 34.6805],
      },
    ],
  },
  {
    id: 2,
    name: "경주 발전소",
    coordinate: [129.2247, 35.8562],

    weather: {
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 28.0,
      windSpeed: 10.0,
    },

    power: {
      currentOutput: 19.0,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },

    faults: [],
  },
  {
    id: 3,
    name: "대구 발전소",
    coordinate: [128.6014, 35.8714],

    weather: {
      weatherType: WEATHER_TYPE.PARTLY_CLOUDY,
      temperature: 30.0,
      windSpeed: 5.0,
    },

    power: {
      currentOutput: 40.0,
      currentPower: 412,
      monthPower: 8.7,
      yearPower: 96.4,
    },

    faults: [
      {
        id: 2,
        date: "07.08",
        time: "11:00",
        status: FAULT_STATUS.WARNING,
      },
    ],
  },
  {
    id: 4,
    name: "장흥 발전소2",
    coordinate: [126.912, 34.684],

    weather: {
      weatherType: WEATHER_TYPE.CLOUDY,
      temperature: 31.0,
      windSpeed: 4.2,
    },

    power: {
      currentOutput: 15.8,
      currentPower: 380,
      monthPower: 7.9,
      yearPower: 88.5,
    },

    faults: [],
  },
];

function MainScreen() {
  const navigate = useNavigate();

  /*
   * 현재는 API 대신 더미 데이터 사용
   */
  const [plants] = useState(dummyPlants);
  const [isPlantsLoading] = useState(false);
  const [plantsError] = useState(null);

  const [screenMode, setScreenMode] = useState(() => {
    return localStorage.getItem("screenMode") || "map";
  });

  const [selectedPlant, setSelectedPlant] = useState(() => {
    const savedPlant =
      localStorage.getItem("selectedPlant");

    return savedPlant
      ? JSON.parse(savedPlant)
      : null;
  });

  const [selectedTurbine, setSelectedTurbine] = useState(() => {
    const savedTurbine =
      localStorage.getItem("selectedTurbine");

    return savedTurbine
      ? JSON.parse(savedTurbine)
      : null;
  });

  useEffect(() => {
    const currentState = {
      screenMode,
      selectedPlant,
      selectedTurbine,
    };

    window.history.replaceState(
      currentState,
      "",
      window.location.href
    );
  }, []);

  useEffect(() => {
    const handlePopState = (event) => {
      const state = event.state;

      if (!state) {
        setScreenMode("map");
        setSelectedPlant(null);
        setSelectedTurbine(null);
        return;
      }

      setScreenMode(state.screenMode || "map");
      setSelectedPlant(
        state.selectedPlant || null
      );
      setSelectedTurbine(
        state.selectedTurbine || null
      );
    };

    window.addEventListener(
      "popstate",
      handlePopState
    );

    return () => {
      window.removeEventListener(
        "popstate",
        handlePopState
      );
    };
  }, []);
  const turbines =
    selectedPlant?.turbines || [];

  useEffect(() => {
    localStorage.setItem(
      "screenMode",
      screenMode
    );
  }, [screenMode]);

  useEffect(() => {
    if (selectedPlant) {
      localStorage.setItem(
        "selectedPlant",
        JSON.stringify(selectedPlant)
      );
    } else {
      localStorage.removeItem(
        "selectedPlant"
      );
    }
  }, [selectedPlant]);

  useEffect(() => {
    if (selectedTurbine) {
      localStorage.setItem(
        "selectedTurbine",
        JSON.stringify(selectedTurbine)
      );
    } else {
      localStorage.removeItem(
        "selectedTurbine"
      );
    }
  }, [selectedTurbine]);

  const moveMode = (
    nextMode,
    nextPlant,
    nextTurbine
  ) => {
    const nextState = {
      screenMode: nextMode,
      selectedPlant: nextPlant,
      selectedTurbine: nextTurbine,
    };

    setScreenMode(nextMode);
    setSelectedPlant(nextPlant);
    setSelectedTurbine(nextTurbine);

    window.history.pushState(
      nextState,
      "",
      window.location.href
    );
  };

  const handleLogout = () => {
    localStorage.removeItem("screenMode");
    localStorage.removeItem("selectedPlant");
    localStorage.removeItem("selectedTurbine");

    navigate("/login");
  };

  const handleSelectPlant = (plant) => {
    moveMode("plant", plant, null);
  };

  const handleSelectTurbine = (turbine) => {
    moveMode(
      "turbine",
      selectedPlant,
      turbine
    );
  };

  const handleBackToMap = () => {
    moveMode("map", null, null);
  };

  return (
    <div className="main-screen">
      <Header
        onLogout={handleLogout}
        onTitleClick={handleBackToMap}
      />

      <div className="dashboard-layout">
        <MainBar
          mode={screenMode}
          plants={plants}
          turbines={turbines}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          isPlantsLoading={isPlantsLoading}
          plantsError={plantsError}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
        />

        <SideBar
          mode={screenMode}
          plants={plants}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
        />

        <UnderBar
          mode={screenMode}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
        />
      </div>
    </div>
  );
}

export default MainScreen;