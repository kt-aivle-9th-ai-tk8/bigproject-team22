import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import "./ReportBox.css";

function getTodayString() {
  return new Date().toISOString().slice(0, 10);
}

function getDateStringBefore(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

function getMonthBeforeString() {
  const date = new Date();
  date.setMonth(date.getMonth() - 1);
  return date.toISOString().slice(0, 10);
}

const EMPTY_REPORT_INFO = {
  reportType: "daily",
  startDate: getTodayString(),
  endDate: getTodayString(),
};

function ReportBox({
  startDate = getTodayString(),
  endDate = getTodayString(),
  onCreateReport,
  isCreating = false,
}) {
  const navigate = useNavigate();

  const [reportInfo, setReportInfo] = useState({
    ...EMPTY_REPORT_INFO,
    startDate,
    endDate,
  });

  const [loadingDotCount, setLoadingDotCount] = useState(1);

  useEffect(() => {
    if (!isCreating) {
      setLoadingDotCount(1);
      return;
    }

    const intervalId = setInterval(() => {
      setLoadingDotCount((prev) =>
        prev >= 3 ? 1 : prev + 1
      );
    }, 500);

    return () => {
      clearInterval(intervalId);
    };
  }, [isCreating]);

  const handleDailyClick = () => {
    const today = getTodayString();

    setReportInfo((prev) => ({
      ...prev,
      reportType: "daily",
      startDate: today,
      endDate: today,
    }));
  };

  const handleWeeklyClick = () => {
    const today = getTodayString();
    const weekAgo = getDateStringBefore(7);

    setReportInfo((prev) => ({
      ...prev,
      reportType: "weekly",
      startDate: weekAgo,
      endDate: today,
    }));
  };

  const handleMonthlyClick = () => {
    const today = getTodayString();
    const monthAgo = getMonthBeforeString();

    setReportInfo((prev) => ({
      ...prev,
      reportType: "monthly",
      startDate: monthAgo,
      endDate: today,
    }));
  };

  const handleStartDateChange = (event) => {
    const today = getTodayString();
    let nextStartDate = event.target.value;

    if (nextStartDate > today) {
      nextStartDate = today;
    }

    setReportInfo((prev) => ({
      ...prev,
      reportType: "custom",
      startDate: nextStartDate,
    }));
  };

  const handleEndDateChange = (event) => {
    const today = getTodayString();
    let nextEndDate = event.target.value;

    if (nextEndDate > today) {
      nextEndDate = today;
    }

    setReportInfo((prev) => ({
      ...prev,
      reportType: "custom",
      endDate: nextEndDate,
    }));
  };

  const handleCreateReport = () => {
    const isEmptyReportInfo =
      !reportInfo.startDate ||
      !reportInfo.endDate;

    if (isEmptyReportInfo) {
      alert("보고서 정보를 입력해주세요.");
      return;
    }

    if (reportInfo.startDate > reportInfo.endDate) {
      alert("종료일은 시작일보다 빠를 수 없습니다.");
      return;
    }

    const reportData = {
      reportKind: "operation",
      reportType: reportInfo.reportType || "custom",
      startDate: reportInfo.startDate,
      endDate: reportInfo.endDate,
    };

    console.log("발전소 운영 보고서 생성 JSON:", reportData);
    onCreateReport?.(reportData);

    setReportInfo({
      ...EMPTY_REPORT_INFO,
      startDate,
      endDate,
    });
  };

  const handleGoToReportList = () => {
    navigate("/reportlist");
  };

  return (
    <div className="report-box">
      <div className="report-info-panel">
        <div className="report-period-section">
          <span className="report-period-title">
            조회 기간
          </span>

          <div className="report-date-row">
            <div className="report-date-field">
              <label>시작일</label>

              <input
                className="report-date-input"
                type="date"
                value={reportInfo.startDate}
                max={getTodayString()}
                onChange={handleStartDateChange}
              />
            </div>

            <span className="report-date-separator">~</span>

            <div className="report-date-field">
              <label>종료일</label>

              <input
                className="report-date-input"
                type="date"
                value={reportInfo.endDate}
                max={getTodayString()}
                onChange={handleEndDateChange}
              />
            </div>
          </div>
        </div>

        <div className="report-type-section">
          <span className="report-type-title">보고서 종류</span>

          <div className="report-type-row">
            <button
              type="button"
              className={reportInfo.reportType === "daily" ? "active" : ""}
              onClick={handleDailyClick}
            >
              일간
            </button>

            <button
              type="button"
              className={reportInfo.reportType === "weekly" ? "active" : ""}
              onClick={handleWeeklyClick}
            >
              주간
            </button>

            <button
              type="button"
              className={reportInfo.reportType === "monthly" ? "active" : ""}
              onClick={handleMonthlyClick}
            >
              월간
            </button>
          </div>
        </div>
      </div>

      <button
        className="report-create-button"
        type="button"
        onClick={handleCreateReport}
        disabled={isCreating}
      >
        {isCreating
          ? `보고서 생성 중${".".repeat(loadingDotCount)}`
          : "보고서 생성"}
      </button>

      <button
        className="report-list-button"
        type="button"
        onClick={handleGoToReportList}
      >
        보고서 목록
      </button>
    </div>
  );
}

export default ReportBox;