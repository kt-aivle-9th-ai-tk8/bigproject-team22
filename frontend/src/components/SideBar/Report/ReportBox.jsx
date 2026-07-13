import { useState } from "react";
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

function ReportBox({
  startDate = getTodayString(),
  endDate = getTodayString(),
}) {
  const [reportType, setReportType] = useState("daily");
  const [selectedStartDate, setSelectedStartDate] = useState(startDate);
  const [selectedEndDate, setSelectedEndDate] = useState(endDate);

  const handleDailyClick = () => {
    const today = getTodayString();

    setReportType("daily");
    setSelectedStartDate(today);
    setSelectedEndDate(today);
  };

  const handleWeeklyClick = () => {
    const today = getTodayString();
    const weekAgo = getDateStringBefore(7);

    setReportType("weekly");
    setSelectedStartDate(weekAgo);
    setSelectedEndDate(today);
  };

  const handleMonthlyClick = () => {
    const today = getTodayString();
    const monthAgo = getMonthBeforeString();

    setReportType("monthly");
    setSelectedStartDate(monthAgo);
    setSelectedEndDate(today);
  };

  const handleStartDateChange = (e) => {
    setSelectedStartDate(e.target.value);
    setReportType(null);
  };

  const handleEndDateChange = (e) => {
    setSelectedEndDate(e.target.value);
    setReportType(null);
  };

  return (
    <div className="report-box">
      <div className="report-date-row">
        <div className="report-date-field">
          <label>조회 기간 시작일</label>
          <input
            type="date"
            value={selectedStartDate}
            onChange={handleStartDateChange}
          />
        </div>

        <span className="report-date-separator">~</span>

        <div className="report-date-field">
          <label>조회 기간 종료일</label>
          <input
            type="date"
            value={selectedEndDate}
            onChange={handleEndDateChange}
          />
        </div>
      </div>

      <div className="report-type-row">
        <button
          className={reportType === "daily" ? "active" : ""}
          onClick={handleDailyClick}
        >
          일간
        </button>

        <button
          className={reportType === "weekly" ? "active" : ""}
          onClick={handleWeeklyClick}
        >
          주간
        </button>

        <button
          className={reportType === "monthly" ? "active" : ""}
          onClick={handleMonthlyClick}
        >
          월간
        </button>
      </div>

      <button
        className="report-create-button"
        onClick={() => {
          console.log({
            reportType,
            startDate: selectedStartDate,
            endDate: selectedEndDate,
          });
        }}
      >
        보고서 생성
      </button>

      <button className="report-list-button">
        보고서 목록
      </button>
    </div>
  );
}

export default ReportBox;