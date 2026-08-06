import React, { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import "./ReportEditScreen.css";

const ALL_REPORTS = [
  { id: 1, plant: "장흥 발전소", turbine: "터빈A", type: "운영보고서", date: "2026-07-20", summary: "장흥 발전소 터빈A 정상 운용 중입니다.", aiDiagnostic: "특이사항 없음." },
  { id: 2, plant: "장흥 발전소", turbine: "터빈C", type: "결함보고서", date: "2026-07-14", summary: "터빈C 날개 미세 균열 발생 감지.", aiDiagnostic: "긴급 드론 점검 권장." },
  { id: 3, plant: "경주 발전소", turbine: "터빈G", type: "이상보고서", date: "2026-07-10", summary: "터빈G 베어링 온도 상승 추세.", aiDiagnostic: "72시간 내 점검 필요." },
  { id: 4, plant: "대구 발전소", turbine: "터빈E", type: "결함보고서", date: "2026-07-01", summary: "터빈E 센서 통신 오작동.", aiDiagnostic: "통신 모듈 교체 권장." },
  { id: 5, plant: "양산 발전소", turbine: "터빈I", type: "이상보고서", date: "2026-06-25", summary: "터빈I 진동 수치 증가.", aiDiagnostic: "진동 진단 실시 필요." },
  { id: 6, plant: "대구 발전소", turbine: "터빈D", type: "운영보고서", date: "2026-06-20", summary: "대구 발전소 터빈D 정기 점검 완료.", aiDiagnostic: "가동 상태 양호." },
];

function ReportEditScreen() {
  const navigate = useNavigate();
  const location = useLocation();

  const passedReport = location.state && location.state.report ? location.state.report : ALL_REPORTS[0];

  const [activeReport, setActiveReport] = useState(passedReport);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    title: `${passedReport.plant || ''} [${passedReport.turbine || ''}] ${passedReport.type || ''}`,
    summary: passedReport.summary || "",
    aiDiagnostic: passedReport.aiDiagnostic || "",
  });

  useEffect(() => {
    if (location.state && location.state.report) {
      const rep = location.state.report;
      setActiveReport(rep);
      setFormData({
        title: `${rep.plant || ''} [${rep.turbine || ''}] ${rep.type || ''}`,
        summary: rep.summary || "",
        aiDiagnostic: rep.aiDiagnostic || "",
      });
      setIsEditing(false);
    }
  }, [location.state]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSave = () => {
    alert("보고서가 성공적으로 저장되었습니다.");
    setIsEditing(false);
  };

  return (
    <div className="report-edit-container">
      {/* 1. 상단 헤더 ('목록으로 돌아가기' 제거, 액션 버튼만 유지) */}
      <header className="edit-header">
        <h1 className="page-title">보고서 상세 및 편집</h1>
        <div className="header-actions">
          {isEditing ? (
            <>
              <button className="btn-secondary" onClick={() => setIsEditing(false)}>취소</button>
              <button className="btn-primary" onClick={handleSave}>저장하기</button>
            </>
          ) : (
            <button className="btn-primary" onClick={() => setIsEditing(true)}>✏️ 보고서 수정</button>
          )}
        </div>
      </header>

      {/* 2. 상단: 메인 보고서 카드 */}
      <main className="edit-content-card">
        <div className="card-title-row">
          <h2>{formData.title}</h2>
          <span className={`type-badge ${activeReport.type}`}>{activeReport.type}</span>
        </div>

        <div className="edit-grid-layout">
          <div className="form-group">
            <label className="form-label">보고서 요약</label>
            {isEditing ? (
              <textarea
                name="summary"
                className="styled-textarea"
                rows={6}
                value={formData.summary}
                onChange={handleChange}
              />
            ) : (
              <div className="readonly-box">{formData.summary}</div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">AI 고장 예측 소견</label>
            {isEditing ? (
              <textarea
                name="aiDiagnostic"
                className="styled-textarea"
                rows={4}
                value={formData.aiDiagnostic}
                onChange={handleChange}
              />
            ) : (
              <div className="readonly-box ai-box">{formData.aiDiagnostic}</div>
            )}
          </div>
        </div>
      </main>

      {/* 3. 하단: ReportListScreen 디자인과 완전히 동일한 보고서 리스트 */}
      <section className="bottom-list-section">
        <div className="bottom-list-header">
          <h3>다른 보고서 목록</h3>
          <button className="view-all-btn" onClick={() => navigate("/report-list")}>전체글 보기</button>
        </div>

        <div className="report-list">
          {ALL_REPORTS.map((report) => (
            <div 
              key={report.id} 
              className={`report-card ${report.id === activeReport.id ? "active-card" : ""}`}
              onClick={() => navigate("/report-edit", { state: { report } })}
              style={{ cursor: "pointer" }}
            >
              <div className="report-content">
                <div className="card-main-row">
                  <h3 className="plant-name">
                    {report.plant} <span className="turbine-tag">[{report.turbine}]</span>
                  </h3>
                  <span className={`type-badge ${report.type}`}>{report.type}</span>
                  <span className="report-date">{report.date}</span>
                </div>
              </div>
              <div className="card-arrow"><span>›</span></div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export default ReportEditScreen;