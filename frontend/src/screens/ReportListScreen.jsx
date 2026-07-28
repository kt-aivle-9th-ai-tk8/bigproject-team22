import React, { useState, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import "./ReportListScreen.css";

// 발전소별 소속 터빈 데이터 매핑
const PLANT_TURBINE_MAP = {
  "장흥 발전소": ["터빈A", "터빈B", "터빈C"],
  "대구 발전소": ["터빈D", "터빈E", "터빈F"],
  "경주 발전소": ["터빈G", "터빈H"],
  "양산 발전소": ["터빈I", "터빈J"],
};

// 피드백 반영 더미 데이터
const INITIAL_REPORTS = [
  { id: 1, plant: "장흥 발전소", turbine: "터빈A", type: "운영보고서", date: "2026-07-20" },
  { id: 2, plant: "장흥 발전소", turbine: "터빈C", type: "결함보고서", date: "2026-07-14" },
  { id: 3, plant: "경주 발전소", turbine: "터빈G", type: "이상보고서", date: "2026-07-10" },
  { id: 4, plant: "대구 발전소", turbine: "터빈E", type: "결함보고서", date: "2026-07-01" },
  { id: 5, plant: "양산 발전소", turbine: "터빈I", type: "이상보고서", date: "2026-06-25" },
  { id: 6, plant: "대구 발전소", turbine: "터빈D", type: "운영보고서", date: "2026-06-20" },
];

function ReportListScreen() {
  const location = useLocation();
  const navigate = useNavigate();

  const queryParams = new URLSearchParams(location.search);
  const initialPlant = queryParams.get("plant") || "전체";
  const initialTurbine = queryParams.get("turbine") || "전체";

  const [selectedType, setSelectedType] = useState("전체");
  const [selectedPlant, setSelectedPlant] = useState(initialPlant);
  const [selectedTurbine, setSelectedTurbine] = useState(initialTurbine);

  const [sortKey, setSortKey] = useState("date"); // date(날짜순), title(가나다순)
  const [isAscending, setIsAscending] = useState(false);

  const todayString = new Date().toISOString().slice(0, 10).replace(/-/g, ".");

  // 발전소 선택 변경 시 처리
  const handlePlantChange = (e) => {
    const newPlant = e.target.value;
    setSelectedPlant(newPlant);
    setSelectedTurbine("전체"); // 발전소가 바뀌면 터빈 선택값 초기화
  };

  // 선택된 발전소에 따른 터빈 목록 동적 계산
  const availableTurbines = useMemo(() => {
    if (selectedPlant === "전체") {
      return Object.values(PLANT_TURBINE_MAP).flat();
    }
    return PLANT_TURBINE_MAP[selectedPlant] || [];
  }, [selectedPlant]);

  // 필터 초기화 기능
  const handleResetFilters = () => {
    setSelectedType("전체");
    setSelectedPlant("전체");
    setSelectedTurbine("전체");
    setSortKey("date");
    setIsAscending(false);
  };

  // 필터가 하나라도 변경되었는지 체크
  const isFiltered =
    selectedType !== "전체" ||
    selectedPlant !== "전체" ||
    selectedTurbine !== "전체" ||
    sortKey !== "date" ||
    isAscending !== false;

  // 필터링 및 정렬 로직
  const filteredAndSortedReports = useMemo(() => {
    return INITIAL_REPORTS.filter((report) => {
      const matchesType = selectedType === "전체" || report.type === selectedType;
      const matchesPlant = selectedPlant === "전체" || report.plant === selectedPlant;
      const matchesTurbine = selectedTurbine === "전체" || report.turbine === selectedTurbine;

      return matchesType && matchesPlant && matchesTurbine;
    }).sort((a, b) => {
      let comparison = 0;
      if (sortKey === "date") {
        comparison = a.date.localeCompare(b.date);
      } else if (sortKey === "title") {
        const nameA = `${a.plant} ${a.turbine}`;
        const nameB = `${b.plant} ${b.turbine}`;
        comparison = nameA.localeCompare(nameB, "ko");
      }

      return isAscending ? comparison : -comparison;
    });
  }, [selectedType, selectedPlant, selectedTurbine, sortKey, isAscending]);

  return (
    <div className="report-list-container">
      {/* 상단 헤더 */}
      <header className="report-header">
        <div className="report-title-group">
          <h1>지난 보고서 리스트</h1>
          <span className="current-date">{todayString}</span>
        </div>

        <div className="header-btn-group">
          <button className="logout-btn" onClick={() => navigate("/login")}>
            로그아웃
          </button>
          <button className="back-btn" onClick={() => navigate(-1)}>
            뒤로가기
          </button>
        </div>
      </header>

      {/* 필터 & 정렬 컨트롤 바 */}
      <div className="controls-row">
        {/* 좌측 필터 드롭다운 그룹 */}
        <div className="filter-controls">
          <select
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value)}
            className="select-box"
          >
            <option value="전체">유형: 전체</option>
            <option value="이상보고서">이상보고서</option>
            <option value="운영보고서">운영보고서</option>
            <option value="결함보고서">결함보고서</option>
          </select>

          <select
            value={selectedPlant}
            onChange={handlePlantChange}
            className="select-box"
          >
            <option value="전체">발전소: 전체</option>
            {Object.keys(PLANT_TURBINE_MAP).map((plantName) => (
              <option key={plantName} value={plantName}>
                {plantName}
              </option>
            ))}
          </select>

          <select
            value={selectedTurbine}
            onChange={(e) => setSelectedTurbine(e.target.value)}
            className="select-box"
          >
            <option value="전체">터빈: 전체</option>
            {availableTurbines.map((turbineName) => (
              <option key={turbineName} value={turbineName}>
                {turbineName}
              </option>
            ))}
          </select>

          {/* 조건 변경 시 보여지는 초기화 버튼 */}
          {isFiltered && (
            <button className="reset-btn" onClick={handleResetFilters}>
              초기화
            </button>
          )}
        </div>

        {/* 우측 정렬 컨트롤 */}
        <div className="sort-controls">
          <select
            value={sortKey}
            onChange={(e) => setSortKey(e.target.value)}
            className="select-box"
          >
            <option value="date">날짜순</option>
            <option value="title">가나다순</option>
          </select>

          <button
            className="sort-btn"
            onClick={() => setIsAscending(!isAscending)}
          >
            {isAscending ? "▲ 오름차순" : "▼ 내림차순"}
          </button>
        </div>
      </div>

      {/* 보고서 카드 리스트 */}
      <main className="report-list">
        {filteredAndSortedReports.length > 0 ? (
          filteredAndSortedReports.map((report) => (
            <div key={report.id} className="report-card">
              <div className="report-content">
                <div className="card-main-row">
                  <h3 className="plant-name">
                    {report.plant}{" "}
                    <span className="turbine-tag">[{report.turbine}]</span>
                  </h3>
                  <span className={`type-badge ${report.type}`}>{report.type}</span>
                  <span className="report-date">{report.date}</span>
                </div>
              </div>
              <div className="card-arrow">
                <span>›</span>
              </div>
            </div>
          ))
        ) : (
          <div className="no-data">조건에 일치하는 보고서가 없습니다.</div>
        )}
      </main>
    </div>
  );
}

export default ReportListScreen;