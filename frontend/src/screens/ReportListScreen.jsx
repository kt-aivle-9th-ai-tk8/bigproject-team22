import React, { useState, useEffect, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { useReportList } from "../hooks/useReportList";

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
    reports,
    loading,
    error,
  } = useReportList();

  // 로그인 유저 담당 발전소 목록 상태
  const [userPlants, setUserPlants] = useState([]);

  // 필터 및 정렬 상태
  const [selectedType, setSelectedType] = useState("전체");
  const [selectedPlant, setSelectedPlant] = useState("전체");
  const [selectedTurbine, setSelectedTurbine] = useState("전체");
  const [sortKey, setSortKey] = useState("date");
  const [isAscending, setIsAscending] = useState(false);

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

  // 1. 로그인 유저 담당 발전소 정보 로드
  useEffect(() => {
    try {
      const storedUser = localStorage.getItem("userInfo");
      if (storedUser) {
        const parsed = JSON.parse(storedUser);
        const assigned = parsed.plants || parsed.assigned_plants || [];
        setUserPlants(assigned);
      }
    } catch (err) {
      console.error("유저 담당 발전소 로드 에러:", err);
    }
  }, []);

  // [더미 제거 1] 전체 보고서 데이터에서 존재하는 발전소 목록만 동적 추출
  const availablePlantOptions = useMemo(() => {
    // 유저 담당 발전소가 로컬스토리지에 있으면 우선 사용
    if (userPlants.length > 0) return userPlants;

    // 만약 유저 담당 발전소 정보가 없다면 실제 DB 수신 보고서 데이터에서 추출
    const plantSet = new Set(
      reports.map((r) => r.plant || r.wind_farm_name).filter(Boolean)
    );
    return Array.from(plantSet);
  }, [userPlants, reports]);

  // [요구사항 3] 터빈 드롭다운 선택 가능 여부 (유형 또는 발전소 중 하나라도 지정되어야 함)
  const isTurbineSelectable = useMemo(() => {
    return selectedType !== "전체" || selectedPlant !== "전체";
  }, [selectedType, selectedPlant]);

  // [더미 제거 2] 백엔드 데이터 기반으로 해당 발전소/유형에 존재하는 실제 터빈 목록만 추출
  const availableTurbineOptions = useMemo(() => {
    if (!isTurbineSelectable) return [];

    let filtered = reports;

    // 특정 발전소가 선택된 경우
    if (selectedPlant !== "전체") {
      filtered = filtered.filter(
        (r) => (r.plant || r.wind_farm_name) === selectedPlant
      );
    }

    // 특정 유형이 선택된 경우
    if (selectedType !== "전체") {
      filtered = filtered.filter(
        (r) => (r.type || r.report_type) === selectedType
      );
    }

    // 조건에 맞는 실제 터빈 이름 추출 및 중복 제거
    const turbineSet = new Set(
      filtered.map((r) => r.turbine || r.turbine_name).filter(Boolean)
    );
    return Array.from(turbineSet);
  }, [reports, selectedPlant, selectedType, isTurbineSelectable]);

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
    return reports
      .filter((report) => {
        const matchesType =
          selectedType === "전체" ||
          report.type === selectedType ||
          report.report_type === selectedType;
        const matchesPlant =
          selectedPlant === "전체" ||
          report.plant === selectedPlant ||
          report.wind_farm_name === selectedPlant;
        const matchesTurbine =
          selectedTurbine === "전체" ||
          report.turbine === selectedTurbine ||
          report.turbine_name === selectedTurbine;
        return matchesType && matchesPlant && matchesTurbine;
      })
      .sort((a, b) => {
        let comparison = 0;
        const dateA = a.generated_at || "";
        const dateB = b.generated_at || "";
        const plantA = a.plant || a.wind_farm_name || "";
        const plantB = b.plant || b.wind_farm_name || "";
        const turbineA = a.turbine || a.turbine_name || "";
        const turbineB = b.turbine || b.turbine_name || "";

        if (sortKey === "date") {
          comparison = dateA.localeCompare(dateB);
        } else if (sortKey === "title") {
          const nameA = `${plantA} ${turbineA}`;
          const nameB = `${plantB} ${turbineB}`;
          comparison = nameA.localeCompare(nameB, "ko");
        }
        return isAscending ? comparison : -comparison;
      });
  }, [reports, selectedType, selectedPlant, selectedTurbine, sortKey, isAscending]);

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
          <button className="logout-btn" onClick={() => navigate("/login")}>로그아웃</button>
          <button className="back-btn" onClick={() => navigate(-1)}>뒤로가기</button>
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
            <option value="전체">유형: 전체</option>
            <option value="wind_farm_operation">단지 운영 리포트</option>
            <option value="turbine_operation">터빈 운영 리포트</option>
            <option value="defect_diagnosis">결함 진단 리포트</option>
            <option value="anomaly_event">이상 감지 리포트</option>
          </select>

          {/* 2. 발전소 필터 (더미 객체 없이 백엔드 수신/사용자 담당 발전소로 동적 렌더링) */}
          <select value={selectedPlant} onChange={handlePlantChange} className="select-box">
            <option value="전체">발전소: 전체</option>
            {availablePlantOptions.map((plantName) => (
              <option key={plantName} value={plantName}>{plantName}</option>
            ))}
          </select>

          {/* 3. 터빈 필터 (유형이나 발전소가 지정되어야 활성화 및 백엔드 실제 터빈만 동적 렌더링) */}
          <select 
            value={selectedTurbine} 
            onChange={(e) => setSelectedTurbine(e.target.value)} 
            className="select-box"
            disabled={!isTurbineSelectable}
            style={{
              cursor: isTurbineSelectable ? "pointer" : "not-allowed",
              backgroundColor: isTurbineSelectable ? "#ffffff" : "#edf2f7",
              color: isTurbineSelectable ? "#2d3748" : "#a0aec0"
            }}
          >
            <option value="전체">
              {!isTurbineSelectable ? "터빈: 유형 또는 발전소를 지정하세요" : "터빈: 전체"}
            </option>
            {availableTurbineOptions.map((turbineName) => (
              <option key={turbineName} value={turbineName}>{turbineName}</option>
            ))}
          </select>

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
            const plantName = report.plant || report.wind_farm_name || "발전소";
            const turbineName = report.turbine || report.turbine_name || "터빈";
            const reportType =
              String(
                report.report_type ||
                report.type ||
                ""
              ).toLowerCase();

            const typeName =
              REPORT_TYPE_LABEL[reportType] ||
              report.report_type ||
              report.type ||
              "보고서";
            const dateStr = report.generated_at || "";

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
                      {plantName} <span className="turbine-tag">[{turbineName}]</span>
                    </h3>
                    <span className={`type-badge ${reportType}`}>{typeName}</span>
                    <span className="report-date">{dateStr}</span>
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