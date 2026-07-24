import { useState } from "react";
import OperationReportPopup from "./Popup/OperationReportPopup";
import "./ReportBox.css";

function getTodayString() {
  return new Date().toISOString().slice(0, 10);
}

const EMPTY_REPORT_INFO = {
  reportType: "daily",
  startDate: getTodayString(),
  endDate: getTodayString(),
  content: "",
};

function ReportBox({
  startDate = getTodayString(),
  endDate = getTodayString(),
  onCreateReport,
}) {
  const [isPopupOpen, setIsPopupOpen] = useState(false);

  const [reportInfo, setReportInfo] = useState({
    ...EMPTY_REPORT_INFO,
    startDate,
    endDate,
  });

  const handleInfoClick = () => {
    setIsPopupOpen(true);
  };

  const handlePopupComplete = (popupData) => {
    console.log("발전소 운영 보고서 팝업 입력 데이터:", popupData);

    setReportInfo(popupData);
    setIsPopupOpen(false);
  };

  const handleCreateReport = () => {
    const isEmptyReportInfo =
      !reportInfo.startDate ||
      !reportInfo.endDate ||
      !reportInfo.reportType;

    if (isEmptyReportInfo) {
      alert("보고서 정보를 입력해주세요.");
      return;
    }

    const reportData = {
      reportKind: "operation",
      reportType: reportInfo.reportType,
      startDate: reportInfo.startDate,
      endDate: reportInfo.endDate,
      content: reportInfo.content,
    };

    console.log("발전소 운영 보고서 생성 JSON:", reportData);
    onCreateReport?.(reportData);

    setReportInfo({
      ...EMPTY_REPORT_INFO,
      startDate,
      endDate,
    });
  };

  const getReportTypeLabel = (type) => {
    if (type === "daily") return "일간";
    if (type === "weekly") return "주간";
    if (type === "monthly") return "월간";
    return "-";
  };

  const displayPeriod =
    reportInfo.startDate && reportInfo.endDate
      ? `${reportInfo.startDate} ~ ${reportInfo.endDate}`
      : "-";

  const displayReportType = getReportTypeLabel(reportInfo.reportType);
  const displayContent = reportInfo.content?.trim() || "-";

  return (
    <>
      <div className="report-box">
        <button
          className="report-info-button"
          type="button"
          onClick={handleInfoClick}
        >
          <div className="report-info-row">
            <span className="report-info-label">조회 기간</span>
            <span className="report-info-value">{displayPeriod}</span>
          </div>

          <div className="report-info-row">
            <span className="report-info-label">보고서 종류</span>
            <span className="report-info-value">{displayReportType}</span>
          </div>

          <div className="report-info-row">
            <span className="report-info-label">추가 내용</span>
            <span className="report-info-value">{displayContent}</span>
          </div>
        </button>

        <button
          className="report-create-button"
          type="button"
          onClick={handleCreateReport}
        >
          보고서 생성
        </button>

        <button className="report-list-button" type="button">
          보고서 목록
        </button>
      </div>

      {isPopupOpen && (
        <OperationReportPopup
          initialData={reportInfo}
          onClose={() => setIsPopupOpen(false)}
          onComplete={handlePopupComplete}
        />
      )}
    </>
  );
}

export default ReportBox;