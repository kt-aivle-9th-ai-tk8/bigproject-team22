import React, { useState, useEffect, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import axios from "axios";
import "./ReportListScreen.css";

const PLANT_TURBINE_MAP = {
  "장흥 발전소": ["터빈A", "터빈B", "터빈C"],
  "대구 발전소": ["터빈D", "터빈E", "터빈F"],
  "경주 발전소": ["터빈G", "터빈H"],
  "양산 발전소": ["터빈I", "터빈J"],
};

function ReportListScreen() {
  const location = useLocation();
  const navigate = useNavigate();

  // API 수신 보고서 데이터 및 로딩 상태
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(false);

  // URL Query Parameters 파싱
  const queryParams = new URLSearchParams(location.search);
  const initialPlant = queryParams.get("plant") || "전체";
  const initialTurbine = queryParams.get("turbine") || "전체";

  // 필터 및 정렬 상태
  const [selectedType, setSelectedType] = useState("전체");
  const [selectedPlant, setSelectedPlant] = useState(initialPlant);
  const [selectedTurbine, setSelectedTurbine] = useState(initialTurbine);
  const [sortKey, setSortKey] = useState("date");
  const [isAscending, setIsAscending] = useState(false);

  const todayString = new Date().toISOString().slice(0, 10).replace(/-/g, ".");

  // 백엔드 API 목록 불러오기
  useEffect(() => {
    const fetchReports = async () => {
      setLoading(true);
      try {
        const response = await axios.get("/api/reports");
        if (response.status === 200) {
          // 백엔드 응답이 response.data 또는 response.data.data 형태일 수 있으므로 유연하게 처리
          const data = Array.isArray(response.data) ? response.data : response.data.data || [];
          setReports(data);
        }
      } catch (error) {
        console.error("보고서 목록 조회 에러:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchReports();
  }, []);

  const handlePlantChange = (e) => {
    setSelectedPlant(e.target.value);
    setSelectedTurbine("전체");
  };

  const availableTurbines = useMemo(() => {
    if (selectedPlant === "전체") return Object.values(PLANT_TURBINE_MAP).flat();
    return PLANT_TURBINE_MAP[selectedPlant] || [];
  }, [selectedPlant]);

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

  // 필터링 및 정렬 연산
  const filteredAndSortedReports = useMemo(() => {
    return reports
      .filter((report) => {
        const matchesType = selectedType === "전체" || report.type === selectedType || report.report_type === selectedType;
        const matchesPlant = selectedPlant === "전체" || report.plant === selectedPlant || report.wind_farm_name === selectedPlant;
        const matchesTurbine = selectedTurbine === "전체" || report.turbine === selectedTurbine || report.turbine_name === selectedTurbine;
        return matchesType && matchesPlant && matchesTurbine;
      })
      .sort((a, b) => {
        let comparison = 0;
        const dateA = a.date || a.created_at || "";
        const dateB = b.date || b.created_at || "";
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

  // 카드를 클릭하면 해당 보고서의 ID를 URL에 실어서 방식 A 경로로 이동
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
          <select value={selectedType} onChange={(e) => setSelectedType(e.target.value)} className="select-box">
            <option value="전체">유형: 전체</option>
            <option value="이상보고서">이상보고서</option>
            <option value="운영보고서">운영보고서</option>
            <option value="결함보고서">결함보고서</option>
          </select>

          <select value={selectedPlant} onChange={handlePlantChange} className="select-box">
            <option value="전체">발전소: 전체</option>
            {Object.keys(PLANT_TURBINE_MAP).map((plantName) => (
              <option key={plantName} value={plantName}>{plantName}</option>
            ))}
          </select>

          <select value={selectedTurbine} onChange={(e) => setSelectedTurbine(e.target.value)} className="select-box">
            <option value="전체">터빈: 전체</option>
            {availableTurbines.map((turbineName) => (
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
            const typeName = report.type || report.report_type || "보고서";
            const dateStr = report.date || report.created_at || "";

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
                    <span className={`type-badge ${typeName}`}>{typeName}</span>
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