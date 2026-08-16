import React, { useState, useEffect, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { useReportList } from "../hooks/useReportList";
import { useReportWindFarms } from "../hooks/useReportWindFarms";
import { useReportWindFarmDetail } from "../hooks/useReportWindFarmDetail";

import "./ReportListScreen.css";

const REPORT_TYPE_LABEL = {
  wind_farm_operation: "단지 운영 리포트",
  turbine_operation: "터빈 운영 리포트",
  defect_diagnosis: "결함 진단 리포트",
  anomaly_event: "이상 감지 리포트",
};

function ReportListScreen() {
  const location = useLocation();
  const navigate = useNavigate();

  const {
    windFarms,
  } = useReportWindFarms();

  // 필터 및 정렬 상태
  const [selectedType, setSelectedType] = useState("전체");
  const [selectedPlant, setSelectedPlant] = useState("전체");
  const [selectedTurbine, setSelectedTurbine] = useState("전체");
  const [sortKey, setSortKey] = useState("date");
  const [isAscending, setIsAscending] = useState(false);
  
  const formatGeneratedAt = (dateStr) => {
    if (!dateStr) return "";

    const [date, time] = dateStr.split("T");

    return `${date.replaceAll("-", ".")} ${time?.slice(0, 5) || ""}`;
  };
  
  const formatReportTitle = (title) => {
    if (!title) return "보고서";

    return title.replace(
      /(\d{4}-\d{2}-\d{2})\s*~\s*\1/g,
      "$1"
    );
  };

  const {
    windFarmDetail: selectedWindFarmDetail,
  } = useReportWindFarmDetail({
    windFarmId:
      selectedPlant !== "전체"
        ? selectedPlant
        : undefined,
  });

  const {
    reports,
    loading,
    error,
  } = useReportList({
    windFarmId:
      selectedPlant !== "전체"
        ? selectedPlant
        : undefined,

    turbineId:
      selectedTurbine !== "전체"
        ? selectedTurbine
        : undefined,

    reportType:
      selectedType !== "전체"
        ? selectedType
        : undefined,
  });

  const todayString = new Date().toISOString().slice(0, 10).replace(/-/g, ".");

  // URL Query Parameter 파싱 및 필터 자동 고정 (대시보드 연동)
  useEffect(() => {
    const queryParams = new URLSearchParams(location.search);
    const plantParam = queryParams.get("plant");
    const turbineParam = queryParams.get("turbine");
    const typeParam = queryParams.get("type");

    if (plantParam) setSelectedPlant(plantParam);
    if (turbineParam) setSelectedTurbine(turbineParam);
    if (typeParam) setSelectedType(typeParam);
  }, [location.search]);

  const availablePlantOptions = useMemo(() => {
    return windFarms
      .map((plant) => ({
        id: plant.id,
        name:
          plant.title ||
          plant.name ||
          plant.wind_farm_name ||
          `발전소 ${plant.id}`,
      }))
      .filter(
        (plant) =>
          plant.id !== undefined &&
          plant.id !== null
      );
  }, [windFarms]);

  // 백엔드 데이터 기반으로 해당 발전소/유형에 존재하는 실제 터빈 목록만 추출
  const availableTurbineOptions = useMemo(() => {
    const turbines =
      selectedWindFarmDetail?.turbines || [];

    return turbines
      .map((turbine) => ({
        id: turbine.id,
        name:
          turbine.name ||
          turbine.code ||
          `터빈 ${turbine.id}`,
      }))
      .filter(
        (turbine) =>
          turbine.id !== undefined &&
          turbine.id !== null
      );
  }, [selectedWindFarmDetail]);

  // 발전소 변경 시 터빈 초기화
  const handlePlantChange = (e) => {
    setSelectedPlant(e.target.value);
    setSelectedTurbine("전체");
  };

  // 유형 변경 시 터빈 리셋 조건 확인
  const handleTypeChange = (e) => {
    const newType = e.target.value;
    setSelectedType(newType);
    if (newType === "전체" && selectedPlant === "전체") {
      setSelectedTurbine("전체");
    }
  };

  const handleResetFilters = () => {
    setSelectedType("전체");
    setSelectedPlant("전체");
    setSelectedTurbine("전체");
    setSortKey("date");
    setIsAscending(false);
  };

  const isFiltered =
    selectedType !== "전체" ||
    selectedPlant !== "전체" ||
    selectedTurbine !== "전체" ||
    sortKey !== "date" ||
    isAscending !== false;

  // 보고서 필터링 및 정렬
  const filteredAndSortedReports = useMemo(() => {
    return [...reports].sort((a, b) => {
      let comparison = 0;

      const dateA = a.generated_at || "";
      const dateB = b.generated_at || "";

      if (sortKey === "date") {
        comparison =
          dateA.localeCompare(dateB);
      } else if (sortKey === "title") {
        const titleA = a.title || "";
        const titleB = b.title || "";

        comparison =
          titleA.localeCompare(
            titleB,
            "ko"
          );
      }

      return isAscending
        ? comparison
        : -comparison;
    });
  }, [
    reports,
    sortKey,
    isAscending,
  ]);

  const handleCardClick = (report) => {
    const reportId = report.id || report.report_id;
    navigate(`/reports/${reportId}/edit`);
  };

  return (
    <div className="report-list-container">
      <header className="report-header">
        <div className="report-title-group">
          <h1>지난 보고서 리스트</h1>
          <span className="current-date">{todayString}</span>
        </div>
        <div className="header-btn-group">
          <button
            className="back-btn"
            onClick={() => navigate("/main")}
          >
            홈
          </button>
        </div>
      </header>

      <div className="controls-row">
        <div className="filter-controls">
          {/* 1. 유형 필터 */}
          <select
            value={selectedType}
            onChange={handleTypeChange}
            className="select-box"
          >
            <option value="전체">
              유형: 전체
            </option>

            <option value="WIND_FARM_OPERATION">
              단지 운영 리포트
            </option>

            <option value="TURBINE_OPERATION">
              터빈 운영 리포트
            </option>

            <option value="DEFECT_DIAGNOSIS">
              결함 진단 리포트
            </option>

            <option value="ANOMALY_EVENT">
              이상 감지 리포트
            </option>
          </select>

          {/* 2. 발전소 필터 (더미 객체 없이 백엔드 수신/사용자 담당 발전소로 동적 렌더링) */}
          <select
            value={selectedPlant}
            onChange={handlePlantChange}
            className="select-box"
          >
            <option value="전체">
              발전소: 전체
            </option>

            {availablePlantOptions.map((plant) => (
              <option
                key={plant.id}
                value={String(plant.id)}
              >
                {plant.name}
              </option>
            ))}
          </select>
          {/* 3. 터빈 필터 (유형이나 발전소가 지정되어야 활성화 및 백엔드 실제 터빈만 동적 렌더링) */}
          {selectedPlant !== "전체" && (
            <select
              value={selectedTurbine}
              onChange={(e) =>
                setSelectedTurbine(e.target.value)
              }
              className="select-box"
            >
              <option value="전체">
                터빈: 전체
              </option>

              {availableTurbineOptions.map((turbine) => (
                <option
                  key={turbine.id}
                  value={String(turbine.id)}
                >
                  {turbine.name}
                </option>
              ))}
            </select>
          )}

          {isFiltered && (
            <button className="reset-btn" onClick={handleResetFilters}>초기화</button>
          )}
        </div>

        <div className="sort-controls">
          <select value={sortKey} onChange={(e) => setSortKey(e.target.value)} className="select-box">
            <option value="date">날짜순</option>
            <option value="title">가나다순</option>
          </select>
          <button className="sort-btn" onClick={() => setIsAscending(!isAscending)}>
            {isAscending ? "▲ 오름차순" : "▼ 내림차순"}
          </button>
        </div>
      </div>

      <main className="report-list">
        {loading ? (
          <div className="no-data">보고서 목록을 불러오는 중입니다...</div>
        ) : filteredAndSortedReports.length > 0 ? (
          filteredAndSortedReports.map((report) => {
            const reportId = report.id || report.report_id;
            const dateStr = formatGeneratedAt(
              report.generated_at
            );

            return (
              <div 
                key={reportId} 
                className="report-card" 
                onClick={() => handleCardClick(report)}
                style={{ cursor: "pointer" }}
              >
                <div className="report-content">
                  <div className="card-main-row">
                    <h3 className="plant-name">
                      {formatReportTitle(report.title)}
                    </h3>

                    <span className="report-date">
                      {dateStr}
                    </span>
                  </div>
                </div>
                <div className="card-arrow"><span>›</span></div>
              </div>
            );
          })
        ) : (
          <div className="no-data">조건에 일치하는 보고서가 없습니다.</div>
        )}
      </main>
    </div>
  );
}

export default ReportListScreen;