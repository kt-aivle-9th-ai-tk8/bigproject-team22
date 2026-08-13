import React, { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";

import ReactMarkdown from "react-markdown";

import "./ReportEditScreen.css";

import { useReportDetail } from "../hooks/useReportDetail";
import { useReportList } from "../hooks/useReportList";
import { useUpdateReport } from "../hooks/useUpdateReport";

function ReportEditScreen() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  // 상단 메인 보고서 상세 데이터
  const [activeReport, setActiveReport] = useState(null);

  const [isEditing, setIsEditing] = useState(false);

  const {
    reports: allReports,
  } = useReportList();
  
  const [formData, setFormData] = useState({
    title: "",
    summary: "",
    aiDiagnostic: "",
    context: "",
  });

  const targetReportId =
    reportId ||
    location.state?.report?.id ||
    location.state?.report?.report_id;

  const {
    reportDetail,
    loading,
    refetch: refetchReportDetail,
  } = useReportDetail({
    reportId: targetReportId,
  });

  const {
    updateReport,
    isUpdating,
  } = useUpdateReport();

  const handleCancelEdit = () => {
    setFormData((prev) => ({
      ...prev,
      summary:
        reportDetail?.context || "",
      context:
        reportDetail?.context || "",
    }));

    setIsEditing(false);
  };

  useEffect(() => {
    if (!reportDetail) {
      return;
    }
    setActiveReport(reportDetail);

    const plantStr =
      reportDetail.plant ||
      reportDetail.wind_farm_name ||
      "발전소";

    const turbineStr =
      reportDetail.turbine ||
      reportDetail.turbine_name ||
      "터빈";

    const typeStr =
      reportDetail.type ||
      reportDetail.report_type ||
      "보고서";

    setFormData({
      title:
        reportDetail.title ||
        `${plantStr} [${turbineStr}] ${typeStr}`,
      summary:
        reportDetail.context ||
        "",
      aiDiagnostic:
        reportDetail.aiDiagnostic ||
        reportDetail.ai_diagnostic ||
        "특이사항 없음.",
      context:
        reportDetail.context ||
        "",
    });
  }, [reportDetail]);

  useEffect(() => {
    setIsEditing(false);
  }, [reportId]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 3. 보고서 수정 저장 처리 (PATCH /api/reports/{report_id})
  const handleSave = async () => {
    const currentId =
      reportId ||
      (
        activeReport &&
        (
          activeReport.id ||
          activeReport.report_id
        )
      );


    if (!currentId) {
      return;
    }


    try {
      await updateReport({
        reportId: currentId,
        context: formData.summary,
      });


      alert(
        "보고서가 성공적으로 저장되었습니다."
      );


      setIsEditing(false);


      refetchReportDetail();
    } catch (error) {
      console.error(
        "보고서 저장 실패:",
        error
      );


      alert(
        error.message ||
        "보고서 저장 중 오류가 발생했습니다."
      );
    }
  };


  const visibleReports = useMemo(() => {
    if (!activeReport) {
      return [];
    }

    const currentId =
      activeReport.id ||
      activeReport.report_id;

    const currentIndex =
      allReports.findIndex((report) => {
        const id =
          report.id ||
          report.report_id;

        return String(id) ===
          String(currentId);
      });

    if (currentIndex === -1) {
      return [];
    }

    return allReports.slice(
      currentIndex + 1
    );
  }, [
    allReports,
    activeReport,
  ]);

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
              <button
                className="btn-secondary"
                onClick={handleCancelEdit}
              >
                취소
              </button>
              <button
                className="btn-primary"
                onClick={handleSave}
                disabled={isUpdating}
              >
                {isUpdating
                  ? "저장 중..."
                  : "저장하기"}
              </button>
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
                  <div className="readonly-box">
                    <ReactMarkdown>
                      {formData.summary}
                    </ReactMarkdown>
                  </div>
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
          {visibleReports.map((report) => {
            const currentReportId = report.id || report.report_id;
            const activeId = activeReport ? (activeReport.id || activeReport.report_id) : null;
            const plantName = report.plant || report.wind_farm_name || "발전소";
            const turbineName = report.turbine || report.turbine_name || "터빈";
            const typeName = report.type || report.report_type || "보고서";
             const dateStr = report.generated_at || "";

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