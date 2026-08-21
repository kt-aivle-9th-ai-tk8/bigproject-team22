import React, { useState, useEffect, useMemo, useRef } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import mermaid from "mermaid";

import "./ReportEditScreen.css";

import { useReportDetail } from "../hooks/useReportDetail";
import { useReportList } from "../hooks/useReportList";
import { useUpdateReport } from "../hooks/useUpdateReport";
import { useDeleteReport } from "../hooks/useDeleteReport";

mermaid.initialize({
  startOnLoad: false,
  theme: "default",
  securityLevel: "loose",
});

// Mermaid 전용 차트 렌더러 컴포넌트
const MermaidChart = ({ chartCode }) => {
  const containerRef = useRef(null);

  const preprocessChartCode = (code) => {
    let processed = code;

    const numbers = [];
    const lineOrBarMatches = processed.matchAll(/(?:line|bar)\s*\[([^\]]+)\]/g);
    for (const match of lineOrBarMatches) {
      const vals = match[1]
        .split(",")
        .map((v) => parseFloat(v.replace(/,/g, "").trim()))
        .filter((v) => !isNaN(v));
      numbers.push(...vals);
    }

    let maxVal = numbers.length > 0 ? Math.max(...numbers) : 100;
    if (maxVal <= 0) maxVal = 10;
    
    const calculatedMax = Math.ceil((maxVal * 1.2) / 10) * 10;

    processed = processed.replace(
      /y-axis\s+("([^"]*)"|[^\d\s]+)?\s*\d+\s*-->\s*\d+/g,
      (match, unit) => {
        const unitStr = unit ? unit.trim() : '"MWh"';
        return `y-axis ${unitStr} 0 --> ${calculatedMax}`;
      }
    );

    const xAxisMatch = processed.match(/x-axis\s*\[([^\]]+)\]/);
    if (xAxisMatch) {
      const items = xAxisMatch[1].split(",").map((s) => s.trim());
      if (items.length <= 1) {
        processed = processed.replaceAll("line [", "bar [");
      }
    }

    return processed;
  };

  useEffect(() => {
    if (!containerRef.current || !chartCode) return;

    const renderChart = async () => {
      try {
        const uniqueId = `mermaid-${Math.random().toString(36).substr(2, 9)}`;
        const fixedCode = preprocessChartCode(chartCode.trim());
        const { svg } = await mermaid.render(uniqueId, fixedCode);
        if (containerRef.current) {
          containerRef.current.innerHTML = svg;
        }
      } catch (err) {
        
        if (containerRef.current) {
          containerRef.current.innerHTML = `<pre style="color: #e53e3e; background: #fff5f5; padding: 12px; border-radius: 6px;">차트 렌더링 실패: \n${chartCode}</pre>`;
        }
      }
    };

    renderChart();
  }, [chartCode]);

  return (
    <div
      ref={containerRef}
      className="mermaid-chart-container"
      style={{ margin: "20px 0", textAlign: "center" }}
    />
  );
};

// 1. 보고서 유형 영문 -> 한글
const formatReportType = (type) => {
  if (!type) return "보고서";
  const upperType = String(type).toUpperCase();
  switch (upperType) {
    case "WIND_FARM_OPERATION":
      return "단지 운영보고서";
    case "TURBINE_OPERATION":
      return "터빈 운영보고서";
    case "ANOMALY_EVENT":
    case "ANOMALY":
      return "이상 분석보고서";
    case "DEFECT":
      return "결함 보고서";
    default:
      return type;
  }
};

// 2. ISO 날짜 포맷팅 함수
const formatGeneratedAt = (dateStr) => {
  if (!dateStr) return "";

  const [date, time] = dateStr.split("T");

  return `${date.replaceAll("-", ".")} ${
    time?.slice(0, 5) || ""
  }`;
};

const formatReportTitle = (title) => {
  if (!title) return "보고서";

  return title.replace(
    /(\d{4}-\d{2}-\d{2})\s*~\s*\1/g,
    "$1"
  );
};

function ReportEditScreen() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [activeReport, setActiveReport] = useState(null);
  const [isEditing, setIsEditing] = useState(false);

  const { reports: allReports } = useReportList();

  const [formData, setFormData] = useState({
    title: "",
    summary: "",
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

  const { updateReport, isUpdating } = useUpdateReport();
  const { deleteReport } = useDeleteReport();

  // 보고서 삭제 처리
  const handleDelete = async () => {
    const currentId =
      reportId || (activeReport && (activeReport.id || activeReport.report_id));

    if (!currentId) {
      alert("보고서 ID가 없습니다.");
      return;
    }

    const isConfirmed = window.confirm("해당 보고서를 삭제하시겠습니까?");
    if (!isConfirmed) return;

    try {
      await deleteReport(currentId);
      alert("보고서가 삭제되었습니다.");
      navigate("/reportlist");
    } catch (error) {
      
      alert(error.message || "보고서 삭제 중 오류가 발생했습니다.");
    }
  };

  const handleCancelEdit = () => {
    setFormData({
      title: reportDetail?.title || "",
      summary: reportDetail?.context || "",
      context: reportDetail?.context || "",
    });
    setIsEditing(false);
  };

  useEffect(() => {
    if (!reportDetail) return;

    setActiveReport(reportDetail);

    const plantStr =
      reportDetail.plant_name ||
      reportDetail.plant ||
      reportDetail.wind_farm_name ||
      "발전소";

    const turbineStr =
      reportDetail.turbine_name ||
      reportDetail.turbine ||
      reportDetail.turbine_id ||
      "터빈";

    const typeStr = formatReportType(
      reportDetail.report_type || reportDetail.type
    );

    setFormData({
      title:
        reportDetail.title ||
        `${plantStr} [${turbineStr}] ${typeStr}`,
      summary: reportDetail.context || "",
      context: reportDetail.context || "",
    });
  }, [reportDetail]);

  useEffect(() => {
    setIsEditing(false);
  }, [reportId]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 보고서 수정 저장 처리
  const handleSave = async () => {
    const currentId =
      reportId || (activeReport && (activeReport.id || activeReport.report_id));

    if (!currentId) return;

    try {
      await updateReport({
        reportId: currentId,
        context: formData.summary,
      });

      alert("보고서가 성공적으로 저장되었습니다.");
      setIsEditing(false);
      refetchReportDetail();
    } catch (error) {
      
      alert(error.message || "보고서 저장 중 오류가 발생했습니다.");
    }
  };

  const visibleReports = useMemo(() => {
    if (!activeReport || !Array.isArray(allReports)) return [];

    const currentId = activeReport.id || activeReport.report_id;

    const currentIndex = allReports.findIndex((report) => {
      const id = report.id || report.report_id;
      return String(id) === String(currentId);
    });

    if (currentIndex === -1) return [];

    return allReports.slice(currentIndex + 1);
  }, [allReports, activeReport]);

  const handleCardClick = (report) => {
    const targetId = report.id || report.report_id;
    navigate(`/reports/${targetId}/edit`);
  };

  return (
    <div className="report-edit-container">
      {/* 1. 상단 헤더 */}
      <header className="edit-header">
        <div className="header-actions">
          {isEditing ? (
            <>
              <button className="btn-secondary" onClick={handleCancelEdit}>
                취소
              </button>
              <button
                className="btn-primary"
                onClick={handleSave}
                disabled={isUpdating}
              >
                {isUpdating ? "저장 중..." : "저장하기"}
              </button>
            </>
          ) : (
            <>
              <button
                className="btn-primary"
                onClick={() => setIsEditing(true)}
              >
                보고서 수정
              </button>
              <button className="btn-delete-text" onClick={handleDelete}>
                보고서 삭제
              </button>
            </>
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
              <span
                className={`type-badge ${
                  activeReport.report_type || activeReport.type
                }`}
              >
                {formatReportType(
                  activeReport.report_type || activeReport.type
                )}
              </span>
            </div>

            <div className="edit-grid-layout">
              <div className="form-group">
                <label className="form-label">보고서 요약</label>
                {isEditing ? (
                  <textarea
                    name="summary"
                    className="styled-textarea"
                    rows={14}
                    value={formData.summary}
                    onChange={handleChange}
                    placeholder="보고서 내용을 수정해주세요."
                  />
                ) : (
                  <div className="readonly-box">
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm]}
                      components={{
                        code({ node, inline, className, children, ...props }) {
                          const codeText = String(children).replace(/\n$/, "");
                          const isMermaid =
                            className?.includes("language-mermaid") ||
                            codeText.includes("xychart-beta") ||
                            codeText.startsWith("%%{init:");

                          if (!inline && isMermaid) {
                            return <MermaidChart chartCode={codeText} />;
                          }

                          return (
                            <code className={className} {...props}>
                              {children}
                            </code>
                          );
                        },
                      }}
                    >
                      {formData.summary || "작성된 보고서 내용이 없습니다."}
                    </ReactMarkdown>
                  </div>
                )}
              </div>
            </div>
          </>
        ) : (
          <div className="no-data">선택된 보고서 정보가 없습니다.</div>
        )}
      </main>

      {/* 3. 하단: 다른 보고서 목록 */}
      <section className="bottom-list-section">
        <div className="bottom-list-header">
          <h3>다른 보고서 목록</h3>
          <button
            className="view-all-btn"
            onClick={() => navigate("/reportlist")}
          >
            전체글 보기 ›
          </button>
        </div>

        <div className="report-list">
          {visibleReports.map((report) => {
            const currentReportId =
              report.id || report.report_id;

            const dateStr =
              formatGeneratedAt(
                report.generated_at
              );

            return (
              <div
                key={currentReportId}
                className="report-card"
                onClick={() =>
                  handleCardClick(report)
                }
                style={{
                  cursor: "pointer",
                }}
              >
                <div className="report-content">
                  <div className="card-main-row">
                    <h3 className="plant-name">
                      {formatReportTitle(
                        report.title
                      )}
                    </h3>

                    <span className="report-date">
                      {dateStr}
                    </span>
                  </div>
                </div>

                <div className="card-arrow">
                  <span>›</span>
                </div>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

export default ReportEditScreen;