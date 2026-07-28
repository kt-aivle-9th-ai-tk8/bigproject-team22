import { useState } from "react";
import "./OperationReportPopup.css";

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

function OperationReportPopup({
  initialData = {},
  onClose,
  onComplete,
}) {
  const [reportType, setReportType] = useState(
    initialData.reportType || "daily"
  );

  const [selectedStartDate, setSelectedStartDate] = useState(
    initialData.startDate || getTodayString()
  );

  const [selectedEndDate, setSelectedEndDate] = useState(
    initialData.endDate || getTodayString()
  );

  const [content, setContent] = useState(initialData.content || "");

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

  const handleStartDateChange = (event) => {
    const today = getTodayString();
    let nextStartDate = event.target.value;

    if (nextStartDate > today) {
      nextStartDate = today;
    }

    setSelectedStartDate(nextStartDate);
    setReportType(null);
  };

  const handleEndDateChange = (event) => {
    const today = getTodayString();
    let nextEndDate = event.target.value;

    if (nextEndDate > today) {
      nextEndDate = today;
    }

    setSelectedEndDate(nextEndDate);
    setReportType(null);
  };

  const handleComplete = () => {
    if (!selectedStartDate || !selectedEndDate) {
      alert("조회 기간을 입력해주세요.");
      return;
    }

    if (selectedStartDate > selectedEndDate) {
      alert("종료일은 시작일보다 빠를 수 없습니다.");
      return;
    }

    const popupData = {
      reportKind: "operation",
      reportType,
      startDate: selectedStartDate,
      endDate: selectedEndDate,
      content,
    };

    console.log("운영 보고서 팝업 입력 JSON:", popupData);
    onComplete?.(popupData);
  };

  return (
    <div
      className="operation-popup-overlay"
      role="presentation"
      onClick={onClose}
    >
      <section
        className="operation-popup"
        role="dialog"
        aria-modal="true"
        aria-label="발전소 운영 보고서 정보 입력"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="operation-popup-body">
          <h2 className="operation-popup-main-title">
            발전소 운영 보고서
          </h2>

          <div className="operation-popup-section">
            <h3 className="operation-popup-title">
              조회 기간 <span className="required-mark">*</span>
            </h3>

            <div className="operation-date-row">
              <div className="operation-date-field">
                <label>조회 기간 시작일</label>
                <input
                  type="date"
                  value={selectedStartDate}
                  max={getTodayString()}
                  onChange={handleStartDateChange}
                />
              </div>

              <span className="operation-date-separator">~</span>

              <div className="operation-date-field">
                <label>조회 기간 종료일</label>
                <input
                  type="date"
                  value={selectedEndDate}
                  max={getTodayString()}
                  onChange={handleEndDateChange}
                />
              </div>
            </div>
          </div>

          <div className="operation-popup-section">
            <h3 className="operation-popup-title">보고서 종류</h3>

            <div className="operation-type-row">
              <button
                type="button"
                className={reportType === "daily" ? "active" : ""}
                onClick={handleDailyClick}
              >
                일간
              </button>

              <button
                type="button"
                className={reportType === "weekly" ? "active" : ""}
                onClick={handleWeeklyClick}
              >
                주간
              </button>

              <button
                type="button"
                className={reportType === "monthly" ? "active" : ""}
                onClick={handleMonthlyClick}
              >
                월간
              </button>
            </div>
          </div>

          <div className="operation-popup-section">
            <h3 className="operation-popup-title">추가 내용</h3>

            <textarea
              className="operation-content-textarea"
              value={content}
              placeholder="보고서에 포함할 추가 내용을 입력해 주세요."
              onChange={(event) => setContent(event.target.value)}
            />
          </div>
        </div>

        <div className="operation-popup-footer">
          <button
            className="operation-popup-cancel-button"
            type="button"
            onClick={onClose}
          >
            취소
          </button>

          <button
            className="operation-popup-complete-button"
            type="button"
            onClick={handleComplete}
          >
            완료
          </button>
        </div>
      </section>
    </div>
  );
}

export default OperationReportPopup;