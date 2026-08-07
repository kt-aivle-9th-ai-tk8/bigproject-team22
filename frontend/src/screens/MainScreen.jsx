import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";

import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import { useWindFarms } from "../hooks/useWindFarms";

import "./MainScreen.css";
import "../components/Bar.css";

const alarmReports = [
  {
    id: "alarm-001",
    title: "터빈 A 긴급 결함 보고서",
    plantName: "장흥 발전소",
    turbineName: "터빈 A",
    time: "14:20",
    markdown: `
# 터빈 A 긴급 결함 보고서

## 개요
- 발전소: 장흥 발전소
- 터빈: 터빈 A
- 발생 시간: 14:20
- 상태: 긴급

## 주요 내용
터빈 A에서 비정상 진동과 출력 저하가 감지되었습니다.

## 조치 필요 사항
1. 터빈 A 즉시 점검
2. 출력 로그 확인
3. 베어링 온도 및 진동 센서 확인
`,
  },
  {
    id: "alarm-002",
    title: "터빈 B 경고 보고서",
    plantName: "장흥 발전소",
    turbineName: "터빈 B",
    time: "13:45",
    markdown: `
# 터빈 B 경고 보고서

## 개요
- 발전소: 장흥 발전소
- 터빈: 터빈 B
- 발생 시간: 13:45
- 상태: 경고

## 주요 내용
터빈 B의 출력 변동 폭이 기준치를 초과했습니다.

## 권장 조치
- 출력 추이 모니터링
- 풍속 데이터 비교
- 센서 이상 여부 확인
`,
  },
];

function MainScreen() {
  const navigate = useNavigate();

  const [screenMode, setScreenMode] = useState(() => {
    return localStorage.getItem("screenMode") || "map";
  });

  const [selectedPlant, setSelectedPlant] = useState(() => {
    const savedPlant = localStorage.getItem("selectedPlant");
    return savedPlant ? JSON.parse(savedPlant) : null;
  });

  const [selectedTurbine, setSelectedTurbine] = useState(() => {
    const savedTurbine = localStorage.getItem("selectedTurbine");
    return savedTurbine ? JSON.parse(savedTurbine) : null;
  });

  const {
    plants,
    isPlantsLoading,
    plantsError,
  } = useWindFarms({
    mode: screenMode,
    refreshInterval: 10000,
  });

  useEffect(() => {
    if (!selectedPlant) {
      return;
    }

    const updatedPlant = plants.find(
      (plant) => plant.id === selectedPlant.id
    );

    if (updatedPlant) {
      setSelectedPlant(updatedPlant);
    } else if (!isPlantsLoading) {
      setSelectedPlant(null);
      setSelectedTurbine(null);
      setScreenMode("map");
    }
  }, [plants, isPlantsLoading]);

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
      setSelectedPlant(state.selectedPlant || null);
      setSelectedTurbine(state.selectedTurbine || null);
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      window.removeEventListener("popstate", handlePopState);
    };
  }, []);

  const turbines = selectedPlant?.turbines || [];

  useEffect(() => {
    localStorage.setItem("screenMode", screenMode);
  }, [screenMode]);

  useEffect(() => {
    if (selectedPlant) {
      localStorage.setItem(
        "selectedPlant",
        JSON.stringify(selectedPlant)
      );
    } else {
      localStorage.removeItem("selectedPlant");
    }
  }, [selectedPlant]);

  useEffect(() => {
    if (selectedTurbine) {
      localStorage.setItem(
        "selectedTurbine",
        JSON.stringify(selectedTurbine)
      );
    } else {
      localStorage.removeItem("selectedTurbine");
    }
  }, [selectedTurbine]);

  const moveMode = (nextMode, nextPlant, nextTurbine) => {
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
    moveMode("turbine", selectedPlant, turbine);
  };

  const handleBackToMap = () => {
    moveMode("map", null, null);
  };

  const handleCreateInspectionReport = (reportData) => {
    console.log("MainScreen에서 받은 점검 보고서 데이터:", reportData);

    const formData = new FormData();
    formData.append("reportKind", reportData.reportKind);

    if (reportData.file) {
      formData.append("file", reportData.file);
    }
  };

  const handleCreateRepairReport = (repairReportData) => {
    console.log("MainScreen에서 받은 수리 보고서 JSON:", repairReportData);
  };

  const handleNavigateUser = () => {
    navigate("/user");
  };

  return (
    <div className="main-screen">
      <Header
        onLogout={handleLogout}
        onTitleClick={handleBackToMap}
        onMyPage={handleNavigateUser}
        alarm={alarmReports}
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
          plants={plants.slice(0, 3)}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
          onCreateInspectionReport={handleCreateInspectionReport}
          onCreateRepairReport={handleCreateRepairReport}
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