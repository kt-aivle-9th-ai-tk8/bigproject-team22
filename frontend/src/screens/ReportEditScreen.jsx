import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import axios from "axios";
import "./ReportEditScreen.css";

function ReportEditScreen() {
  const { reportId } = useParams(); // URL 파라미터에서 reportId 추출 (/reports/:reportId/edit)
  const navigate = useNavigate();
  const location = useLocation();

  // 상단 메인 보고서 상세 데이터
  const [activeReport, setActiveReport] = useState(null);
  // 하단 보고서 목록 데이터
  const [allReports, setAllReports] = useState([]);

  const [loading, setLoading] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  const [formData, setFormData] = useState({
    title: "",
    summary: "",
    aiDiagnostic: "",
    context: "",
  });

  // 1. 단건 상세 보고서 조회 함수 (GET /api/reports/{report_id})
  const fetchReportDetail = async (id) => {
    setLoading(true);
    try {
      const response = await axios.get(`/api/reports/${id}`);
      if (response.status === 200) {
        const data = response.data.data || response.data;
        setActiveReport(data);

        const plantStr = data.plant || data.wind_farm_name || "발전소";
        const turbineStr = data.turbine || data.turbine_name || "터빈";
        const typeStr = data.type || data.report_type || "보고서";

        setFormData({
          title: data.title || `${plantStr} [${turbineStr}] ${typeStr}`,
          summary: data.summary || data.context || "",
          aiDiagnostic: data.aiDiagnostic || data.ai_diagnostic || "특이사항 없음.",
          context: data.context || "",
        });
      }
    } catch (error) {
      console.error("보고서 상세 조회 에러:", error);
    } finally {
      setLoading(false);
    }
  };

  // 2. 하단 전체 목록 불러오기 함수 (GET /api/reports)
  const fetchAllReports = async () => {
    try {
      const response = await axios.get("/api/reports");
      if (response.status === 200) {
        const list = Array.isArray(response.data) ? response.data : response.data.data || [];
        setAllReports(list);
      }
    } catch (error) {
      console.error("하단 보고서 목록 조회 에러:", error);
    }
  };

  // URL의 reportId가 변경되거나 컴포넌트 마운트 시 데이터 로드
  useEffect(() => {
    // URL에 reportId가 존재하면 단건 API 호출, 없으면 state 전달값 체크
    if (reportId) {
      fetchReportDetail(reportId);
    } else if (location.state && location.state.report) {
      const rep = location.state.report;
      setActiveReport(rep);
      const currentId = rep.id || rep.report_id;
      if (currentId) fetchReportDetail(currentId);
    }
    
    fetchAllReports();
    setIsEditing(false);
  }, [reportId, location.state]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 3. 보고서 수정 저장 처리 (PATCH /api/reports/{report_id})
  const handleSave = async () => {
    const currentId = reportId || (activeReport && (activeReport.id || activeReport.report_id));
    if (!currentId) return;

    try {
      const payload = {
        context: formData.summary || formData.context,
      };

      const response = await axios.patch(`/api/reports/${currentId}`, payload);

      if (response.status === 200) {
        alert("보고서가 성공적으로 저장되었습니다.");
        setIsEditing(false);
        fetchReportDetail(currentId); // 수정 완료 후 데이터 재조회
      }
    } catch (error) {
      console.error("보고서 저장 실패:", error);
      alert(error.response?.data?.message || "보고서 저장 중 오류가 발생했습니다.");
    }
  };

  // 하단 카드를 클릭하면 URL 파라미터를 변경하여 해당 보고서로 이동
  const handleCardClick = (report) => {
    const targetId = report.id || report.report_id;
    navigate(`/reports/${targetId}/edit`);
  };

  return (
    <div className="report-edit-container">
      {/* 1. 상단 헤더 */}
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
        {loading ? (
          <div className="no-data">보고서 상세 정보를 불러오는 중입니다...</div>
        ) : activeReport ? (
          <>
            <div className="card-title-row">
              <h2>{formData.title}</h2>
              <span className={`type-badge ${activeReport.type || activeReport.report_type}`}>
                {activeReport.type || activeReport.report_type}
              </span>
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
          </>
        ) : (
          <div className="no-data">선택된 보고서 정보가 없습니다.</div>
        )}
      </main>

      {/* 3. 하단: 다른 보고서 리스트 */}
      <section className="bottom-list-section">
        <div className="bottom-list-header">
          <h3>다른 보고서 목록</h3>
          <button className="view-all-btn" onClick={() => navigate("/reportlist")}>전체글 보기</button>
        </div>

        <div className="report-list">
          {allReports.map((report) => {
            const currentReportId = report.id || report.report_id;
            const activeId = activeReport ? (activeReport.id || activeReport.report_id) : null;
            const plantName = report.plant || report.wind_farm_name || "발전소";
            const turbineName = report.turbine || report.turbine_name || "터빈";
            const typeName = report.type || report.report_type || "보고서";
            const dateStr = report.date || report.created_at || "";

            return (
              <div 
                key={currentReportId} 
                className={`report-card ${currentReportId === activeId ? "active-card" : ""}`}
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
          })}
        </div>
      </section>
    </div>
  );
}

export default ReportEditScreen;