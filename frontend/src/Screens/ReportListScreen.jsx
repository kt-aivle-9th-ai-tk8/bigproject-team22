import React, { useState } from "react";
import "./ReportListScreen.css";

function ReportListScreen() {
  // 모의 보고서 데이터
  const initialReports = [
    { id: 1, plant: "장흥 발전소", date: "2026-07-14", issue: "블레이드 파손", preview: "보고서 첫 줄 내용 미리보기입니다." },
    { id: 2, plant: "경주 발전소", date: "2026-07-10", issue: "기어박스 소음", preview: "진동 센서 이상 데이터 감지됨." },
    { id: 3, plant: "대구 발전소", date: "2026-07-01", issue: "인버터 과열", preview: "점검 결과 모듈 교체 필요." },
    { id: 4, plant: "양산 발전소", date: "2026-06-25", issue: "전압 불안정", preview: "계통 연계 관련 점검 진행." },
  ];

  const [searchTerm, setSearchTerm] = useState("");
  const [sortBy, setSortBy] = useState("date"); // 'date', 'name', 'plant'
  const [sortOrder, setSortOrder] = useState("desc"); // 'asc', 'desc'

  // 검색 및 정렬 로직
  const filteredReports = initialReports.filter((item) =>
    item.plant.includes(searchTerm) || item.issue.includes(searchTerm) || item.date.includes(searchTerm)
  );

  const sortedReports = [...filteredReports].sort((a, b) => {
    let factorA = a[sortBy === "name" ? "issue" : sortBy === "plant" ? "plant" : "date"];
    let factorB = b[sortBy === "name" ? "issue" : sortBy === "plant" ? "plant" : "date"];

    if (factorA < factorB) return sortOrder === "asc" ? -1 : 1;
    if (factorA > factorB) return sortOrder === "asc" ? 1 : -1;
    return 0;
  });

  return (
    <div className="report-container">
      {/* 상단 헤더 */}
      <header className="report-header">
        <div className="header-title-area">
          <h2>지난 보고서 리스트</h2>
          <span className="current-date">2026.07.21</span>
        </div>
        <button className="logout-btn" onClick={() => window.location.href = "/login"}>로그아웃</button>
      </header>

      {/* 검색 및 정렬 컨트롤 바 */}
      <div className="control-bar">
        <div className="search-box">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="발전소명 또는 내용을 입력하세요"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        {/* 정렬 기능 컴포넌트 */}
        <div className="sort-controls">
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)} className="sort-select">
            <option value="date">날짜순</option>
            <option value="plant">발전소별</option>
            <option value="name">가나다순</option>
          </select>

          <button
            className="order-toggle-btn"
            onClick={() => setSortOrder(sortOrder === "asc" ? "desc" : "asc")}
          >
            {sortOrder === "asc" ? "▲ 오름차순" : "▼ 내림차순"}
          </button>
        </div>
      </div>

      {/* 참고 시안 형태의 카드 리스트 */}
      <main className="report-list">
        {sortedReports.map((report) => (
          <div key={report.id} className="report-card">
            <div className="report-info">
              <div className="card-header">
                <h3>{report.plant}</h3>
                <span className="report-date">{report.date}</span>
              </div>
              <p className="report-issue">{report.issue}</p>
              <p className="report-preview">{report.preview}</p>
            </div>
            <div className="card-action">
              <span className="arrow-icon">›</span>
            </div>
          </div>
        ))}
      </main>
    </div>
  );
}

export default ReportListScreen;