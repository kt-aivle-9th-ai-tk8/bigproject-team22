import React, { useState, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import "./ReportListScreen.css";

const INITIAL_REPORTS = [
  {
    id: 1,
    plant: "장흥 발전소",
    turbine: "터빈A",
    type: "운영보고서",
    date: "2026-07-20",
    issue: "정기 점검 보고",
    preview: "월간 발전량 및 설비 상태 양호.",
  },
  {
    id: 2,
    plant: "장흥 발전소",
    turbine: "터빈C",
    type: "결함보고서",
    date: "2026-07-14",
    issue: "블레이드 파손",
    preview: "보고서 첫 줄 내용 미리보기입니다.",
  },
  {
    id: 3,
    plant: "경주 발전소",
    turbine: "터빈A",
    type: "이상보고서",
    date: "2026-07-10",
    issue: "기어박스 소음",
    preview: "진동 센서 이상 데이터 감지됨.",
  },
  {
    id: 4,
    plant: "대구 발전소",
    turbine: "터빈B",
    type: "결함보고서",
    date: "2026-07-01",
    issue: "인버터 과열",
    preview: "점검 결과 모듈 교체 필요.",
  },
  {
    id: 5,
    plant: "양산 발전소",
    turbine: "터빈A",
    type: "이상보고서",
    date: "2026-06-25",
    issue: "전압 불안정",
    preview: "계통 연계 관련 점검 진행.",
  },
];

function ReportListScreen() {
  const location = useLocation();
  const navigate = useNavigate();

  const queryParams = new URLSearchParams(location.search);
  const initialPlant = queryParams.get("plant") || "전체";
  const initialTurbine = queryParams.get("turbine") || "전체";

  const [searchTerm, setSearchTerm] = useState("");
  const [selectedType, setSelectedType] = useState("전체");
  const [selectedPlant, setSelectedPlant] = useState(initialPlant);
  const [selectedTurbine, setSelectedTurbine] = useState(initialTurbine);

  const [sortKey, setSortKey] = useState("date");
  const [isAscending, setIsAscending] = useState(false);

  const todayString = new Date().toISOString().slice(0, 10).replace(/-/g, ".");

  const filteredAndSortedReports = useMemo(() => {
    return INITIAL_REPORTS.filter((report) => {
      const matchesSearch =
        report.plant.includes(searchTerm) ||
        report.issue.includes(searchTerm) ||
        report.preview.includes(searchTerm);

      const matchesType = selectedType === "전체" || report.type === selectedType;
      const matchesPlant = selectedPlant === "전체" || report.plant === selectedPlant;
      const matchesTurbine = selectedTurbine === "전체" || report.turbine === selectedTurbine;

      return matchesSearch && matchesType && matchesPlant && matchesTurbine;
    }).sort((a, b) => {
      let comparison = 0;
      if (sortKey === "date") {
        comparison = a.date.localeCompare(b.date);
      } else if (sortKey === "plant") {
        comparison = a.plant.localeCompare(b.plant, "ko");
      } else if (sortKey === "issue") {
        comparison = a.issue.localeCompare(b.issue, "ko");
      }

      return isAscending ? comparison : -comparison;
    });
  }, [searchTerm, selectedType, selectedPlant, selectedTurbine, sortKey, isAscending]);

  return (
    <div className="report-list-container">
      {/* 상단 헤더 */}
      <header className="report-header">
        <div className="report-title-group">
          <h1>지난 보고서 리스트</h1>
          <span className="current-date">{todayString}</span>
        </div>

        {/* 우측 상단 버튼 그룹 */}
        <div className="header-btn-group">
          <button className="logout-btn" onClick={() => navigate("/login")}>
            로그아웃
          </button>
          <button className="home-btn" onClick={() => navigate("/main")}>
            뒤로가기
          </button>
        </div>
      </header>

      {/* 1. 검색창 */}
      <div className="search-container">
        <div className="search-box">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="발전소명 또는 내용을 입력하세요"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      {/* 2. 검색창 아래: 필터 & 정렬 영역 */}
      <div className="controls-row">
        {/* 좌측 필터 드롭다운들 */}
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
            onChange={(e) => setSelectedPlant(e.target.value)}
            className="select-box"
          >
            <option value="전체">발전소: 전체</option>
            <option value="장흥 발전소">장흥 발전소</option>
            <option value="대구 발전소">대구 발전소</option>
            <option value="경주 발전소">경주 발전소</option>
            <option value="양산 발전소">양산 발전소</option>
          </select>

          <select
            value={selectedTurbine}
            onChange={(e) => setSelectedTurbine(e.target.value)}
            className="select-box"
          >
            <option value="전체">터빈: 전체</option>
            <option value="터빈A">터빈A</option>
            <option value="터빈B">터빈B</option>
            <option value="터빈C">터빈C</option>
          </select>
        </div>

        {/* 우측 정렬 드롭다운 & 버튼 */}
        <div className="sort-controls">
          <select
            value={sortKey}
            onChange={(e) => setSortKey(e.target.value)}
            className="select-box"
          >
            <option value="date">날짜순</option>
            <option value="plant">발전소별</option>
            <option value="issue">가나다순</option>
          </select>

          <button
            className="sort-btn"
            onClick={() => setIsAscending(!isAscending)}
          >
            {isAscending ? "▲ 오름차순" : "▼ 내림차순"}
          </button>
        </div>
      </div>

      {/* 3. 보고서 카드 리스트 */}
      <main className="report-list">
        {filteredAndSortedReports.length > 0 ? (
          filteredAndSortedReports.map((report) => (
            <div key={report.id} className="report-card">
              <div className="report-content">
                <div className="card-top-row">
                  <h3 className="plant-name">
                    {report.plant}{" "}
                    <span className="turbine-tag">[{report.turbine}]</span>
                  </h3>
                  <span className="report-date">{report.date}</span>
                </div>
                <div className="badge-row">
                  <span className={`type-badge ${report.type}`}>{report.type}</span>
                </div>
                <h4 className="issue-title">{report.issue}</h4>
                <p className="preview-text">{report.preview}</p>
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