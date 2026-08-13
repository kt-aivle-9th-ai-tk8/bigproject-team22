import { useNavigate } from "react-router-dom";
import { useCallback, useEffect, useState } from "react";


import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import { logout } from "../api/authApi";

import { useWindFarmDetail } from "../hooks/useWindFarmDetail";
import { useWindFarms } from "../hooks/useWindFarms";
import { useNotifications } from "../hooks/useNotifications";
import { usePowerGeneration } from "../hooks/usePowerGeneration";
import { useTurbineDetail } from "../hooks/useTurbineDetail";
import { useReportStatusPolling } from "../hooks/useReportStatusPolling";
import { useInspectionReport } from "../hooks/useInspectionReport";
import { useTurbineReports } from "../hooks/useTurbineReports";

import "./MainScreen.css";
import "../components/Bar.css";

function MainScreen() {
  const navigate = useNavigate();

  const [generatedReport, setGeneratedReport] = useState(null);

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

  // 전체 발전소 페이지 api
  const {
    plants,
    isPlantsLoading,
    plantsError,
  } = useWindFarms({
    mode: screenMode,
    refreshInterval: 10000,
  });

  // 발전소 페이지 api
  const {
    windFarmDetail,
    isWindFarmDetailLoading,
    windFarmDetailError,
  } = useWindFarmDetail({
    mode: screenMode,
    windFarmId: selectedPlant?.id,
    refreshInterval: 10000,
  });

  // 터빈 상세 페이지 api
  const {
    turbineDetail,
    isTurbineDetailLoading,
    turbineDetailError,
  } = useTurbineDetail({
    mode: screenMode,
    turbineId: selectedTurbine?.id,
    refreshInterval: 10000,
  });

  // 터빈 상세 페이지 보고서 리스트 api
  const {
    reportItems: turbineReportItems,
  } = useTurbineReports({
    mode: screenMode,
    windFarmId: selectedPlant?.id,
    turbineId: selectedTurbine?.id,
    refreshInterval: 10000,
  });

  // 알림 보고서 api
  const {
    notifications
  } = useNotifications({
    refreshInterval: 10000,
  });

  const {
    createOperationReport,
    reportId: operationReportId,
    isReportCreating,
  } = useReportStatusPolling({
    refreshInterval: 5000,

    onGenerated: (report) => {
      const displayTitle = String(
        report?.title || "운영보고서"
      )
        .replace(
          /\s*\d{4}-\d{2}-\d{2}\s*~\s*\d{4}-\d{2}-\d{2}\s*/g,
          " "
        )
        .replace(/\s+/g, " ")
        .trim();

      setGeneratedReport({
        ...report,
        displayTitle,
      });
    },
  });

  const handleConfirmGeneratedReport = () => {
    const reportId =
      generatedReport?.id ||
      generatedReport?.report_id;

    if (!reportId) {
      alert("보고서 ID가 없습니다.");
      return;
    }

    const report = generatedReport;

    setGeneratedReport(null);

    navigate(`/reports/${reportId}/edit`, {
      state: {
        report,
      },
    });
  };


  const handleCancelGeneratedReport = () => {
    setGeneratedReport(null);
  };

  const isOperationReportPending =
    isReportCreating ||
    Boolean(operationReportId);

  const {
    createInspectionReport,
    reportId: inspectionReportId,
    isInspectionCreating,
    inspectionError,
  } = useInspectionReport({
    refreshInterval: 5000,

    onGenerated: (report) => {
      alert(
        `${report?.title || "점검 보고서"} 생성이 완료되었습니다.`
      );
    },
  });

  const isInspectionReportCreating =
    isInspectionCreating ||
    Boolean(inspectionReportId);

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
  }, [plants, isPlantsLoading, selectedPlant]);

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

  const turbines = windFarmDetail?.turbines || [];

  const underBarPlant =
    screenMode === "map"
      ? plants[0] || null
      : selectedPlant;
  
  const {
    powerData,
    isPowerLoading,
    powerError,
    fetchPowerGeneration,
  } = usePowerGeneration({
    mode: screenMode,
    selectedPlant: underBarPlant,
    selectedTurbine,
  });

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

  const moveMode = useCallback(
    (nextMode, nextPlant, nextTurbine) => {
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
    },
    []
  );

  const handleSelectPlant = useCallback(
    (plant) => {
      moveMode("plant", plant, null);
    },
    [moveMode]
  );

  const handleLogout = async () => {
    try {
      await logout();

      localStorage.removeItem("screenMode");
      localStorage.removeItem("selectedPlant");
      localStorage.removeItem("selectedTurbine");

      navigate("/login");
    } catch (error) {
      console.error(
        "로그아웃 API 에러:",
        error
      );

      alert(error.message);
    }
  };

  const handleSelectTurbine = (turbine) => {
    moveMode("turbine", selectedPlant, turbine);
  };

  const handleBackToMap = () => {
    moveMode("map", null, null);
  };

  const handleCreateInspectionReport = async (
    reportData
  ) => {
    if (!selectedPlant?.id) {
      alert("발전소 정보가 없습니다.");
      return;
    }

    try {
      await createInspectionReport({
        windFarmId: selectedPlant.id,
        reportData,
      });
    } catch (error) {
      alert(error.message);
    }
  };

  const handleCreateOperationReport = async ({
    startDate,
    endDate,
  }) => {
    if (!selectedPlant?.id) {
      alert("발전소 정보가 없습니다.");
      return;
    }

    if (
      screenMode === "turbine" &&
      !selectedTurbine?.id
    ) {
      alert("터빈 정보가 없습니다.");
      return;
    }

    const reportType =
      screenMode === "turbine"
        ? "TURBINE_OPERATION"
        : "WIND_FARM_OPERATION";


    const requestData = {
      windFarmId: selectedPlant.id,

      turbineId:
        screenMode === "turbine"
          ? selectedTurbine.id
          : undefined,

      periodStart: `${startDate}T00:00:00`,
      periodEnd: `${endDate}T23:59:59`,

      reportType,
    };


    console.log(
      "실제 보고서 생성 요청 데이터:",
      requestData
    );


    try {
      await createOperationReport(
        requestData
      );
    } catch (error) {
      alert(error.message);
    }
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
        alarm={notifications}
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
          isWindFarmDetailLoading={isWindFarmDetailLoading}
          windFarmDetailError={windFarmDetailError}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
        />

        <SideBar
          mode={screenMode}
          plants={plants.slice(0, 3)}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          windFarmDetail={windFarmDetail}
          turbineDetail={turbineDetail}
          turbineReportItems={turbineReportItems}
          notifications={notifications}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
          onCreateInspectionReport={handleCreateInspectionReport}
          onCreateOperationReport={handleCreateOperationReport}
          isOperationReportPending={isOperationReportPending}
          isInspectionReportCreating={isInspectionReportCreating}
        />

        <UnderBar
          mode={screenMode}
          selectedPlant={underBarPlant}
          selectedTurbine={selectedTurbine}
          powerData={powerData}
          isLoading={isPowerLoading}
          powerError={powerError}
          onFetchPowerGeneration={fetchPowerGeneration}
        />
      </div>

      {generatedReport && (
        <div className="report-complete-overlay">
          <div className="report-complete-popup">
            <p className="report-complete-message">
              {generatedReport.displayTitle} 보고서 생성이 완료되었습니다.
            </p>

            <p className="report-complete-question">
              보고서를 확인하시겠습니까?
            </p>

            <div className="report-complete-actions">
              <button
                type="button"
                className="report-complete-confirm"
                onClick={handleConfirmGeneratedReport}
              >
                확인
              </button>

              <button
                type="button"
                className="report-complete-cancel"
                onClick={handleCancelGeneratedReport}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default MainScreen;