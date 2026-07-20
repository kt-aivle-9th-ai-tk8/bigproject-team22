import { useState } from "react";
import "./RepairReportPopup.css";

function RepairReportPopup({
  initialData,
  turbineOptions = ["터빈 A", "터빈 B", "터빈 C", "터빈 D"],
  onClose,
  onComplete,
}) {
  const [startDate, setStartDate] = useState(initialData.startDate || "");
  const [startTime, setStartTime] = useState(initialData.startTime || "00:00");
  const [endDate, setEndDate] = useState(initialData.endDate || "");
  const [endTime, setEndTime] = useState(initialData.endTime || "23:59");

  const [turbines, setTurbines] = useState(initialData.turbines || []);
  const [documentId, setDocumentId] = useState(initialData.documentId || "");
  const [content, setContent] = useState(initialData.content || "");
  const [additionalItems, setAdditionalItems] = useState(
    initialData.additionalItems || []
  );

  const isRequiredFilled =
    startDate &&
    startTime &&
    endDate &&
    endTime &&
    turbines.length > 0 &&
    documentId.trim();

  const getDateTimeValue = (date, time) => {
    if (!date || !time) return "";
    return `${date}T${time}`;
  };

  const isStartAfterEnd = (
    nextStartDate,
    nextStartTime,
    nextEndDate,
    nextEndTime
  ) => {
    const startDateTime = getDateTimeValue(nextStartDate, nextStartTime);
    const endDateTime = getDateTimeValue(nextEndDate, nextEndTime);

    if (!startDateTime || !endDateTime) return false;

    return startDateTime > endDateTime;
  };

  const handleStartDateChange = (event) => {
    const nextStartDate = event.target.value;

    setStartDate(nextStartDate);

    if (isStartAfterEnd(nextStartDate, startTime, endDate, endTime)) {
      setEndDate(nextStartDate);
      setEndTime(startTime);
    }
  };

  const handleStartTimeChange = (event) => {
    const nextStartTime = event.target.value;

    setStartTime(nextStartTime);

    if (isStartAfterEnd(startDate, nextStartTime, endDate, endTime)) {
      setEndDate(startDate);
      setEndTime(nextStartTime);
    }
  };

  const handleEndDateChange = (event) => {
    const nextEndDate = event.target.value;

    if (isStartAfterEnd(startDate, startTime, nextEndDate, endTime)) {
      alert("종료일시는 시작일시보다 빠를 수 없습니다.");
      return;
    }

    setEndDate(nextEndDate);
  };

  const handleEndTimeChange = (event) => {
    const nextEndTime = event.target.value;

    if (isStartAfterEnd(startDate, startTime, endDate, nextEndTime)) {
      alert("종료일시는 시작일시보다 빠를 수 없습니다.");
      return;
    }

    setEndTime(nextEndTime);
  };

  const handleTurbineChange = (turbineName) => {
    setTurbines((prev) => {
      if (prev.includes(turbineName)) {
        return prev.filter((item) => item !== turbineName);
      }

      return [...prev, turbineName];
    });
  };

  const handleAddItem = () => {
    setAdditionalItems((prev) => [
      ...prev,
      {
        id: Date.now(),
        title: "",
        content: "",
      },
    ]);
  };

  const handleAdditionalItemChange = (id, field, value) => {
    setAdditionalItems((prev) =>
      prev.map((item) =>
        item.id === id
          ? {
              ...item,
              [field]: value,
            }
          : item
      )
    );
  };

  const handleRemoveAdditionalItem = (id) => {
    setAdditionalItems((prev) => prev.filter((item) => item.id !== id));
  };

  const handleComplete = () => {
    if (!isRequiredFilled) return;

    const popupData = {
      startDate,
      startTime,
      endDate,
      endTime,
      startDateTime: getDateTimeValue(startDate, startTime),
      endDateTime: getDateTimeValue(endDate, endTime),
      turbines,
      documentId,
      content,
      additionalItems: additionalItems.map((item) => ({
        title: item.title,
        content: item.content,
      })),
    };

    console.log("수리 보고서 팝업 입력 JSON:", popupData);
    onComplete?.(popupData);
  };

  return (
    <div className="repair-popup-overlay" role="presentation">
      <section
        className="repair-popup"
        role="dialog"
        aria-modal="true"
        aria-label="수리 보고서 정보 입력"
      >
        <div className="repair-popup-body">
          <div className="repair-popup-section">
            <h2 className="repair-popup-main-title">수리 보고서</h2>
            <h3 className="repair-popup-title">
              수리기간 <span className="required-mark">*</span>
            </h3>

            <div className="repair-date-row">
              <div className="repair-date-field">
                <label>조회 기간 시작일</label>
                <input
                  type="date"
                  value={startDate}
                  max={endDate || undefined}
                  onChange={handleStartDateChange}
                />
              </div>

              <div className="repair-date-field repair-time-field">
                <label>시작 시간</label>
                <input
                  type="time"
                  value={startTime}
                  onChange={handleStartTimeChange}
                />
              </div>

              <span className="repair-date-separator">~</span>

              <div className="repair-date-field">
                <label>조회 기간 종료일</label>
                <input
                  type="date"
                  value={endDate}
                  min={startDate || undefined}
                  onChange={handleEndDateChange}
                />
              </div>

              <div className="repair-date-field repair-time-field">
                <label>종료 시간</label>
                <input
                  type="time"
                  value={endTime}
                  onChange={handleEndTimeChange}
                />
              </div>
            </div>
          </div>

          <div className="repair-popup-section">
            <h3 className="repair-popup-title">
              터빈 <span className="required-mark">*</span>
            </h3>

            <div className="repair-turbine-list">
              {turbineOptions.map((turbineName) => (
                <label className="repair-turbine-option" key={turbineName}>
                  <input
                    type="checkbox"
                    checked={turbines.includes(turbineName)}
                    onChange={() => handleTurbineChange(turbineName)}
                  />
                  <span>{turbineName}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="repair-popup-section">
            <h3 className="repair-popup-title">
              공문 ID <span className="required-mark">*</span>
            </h3>

            <input
              className="repair-document-input"
              type="text"
              value={documentId}
              placeholder="공문 ID를 입력해 주세요"
              onChange={(event) => setDocumentId(event.target.value)}
            />
          </div>

          <div className="repair-popup-section">
            <h3 className="repair-popup-title">수리 내용</h3>

            <textarea
              className="repair-content-textarea"
              value={content}
              placeholder="내용을 입력해 주세요"
              onChange={(event) => setContent(event.target.value)}
            />
          </div>

          {additionalItems.length > 0 && (
            <div className="repair-additional-list">
              {additionalItems.map((item, index) => (
                <div className="repair-additional-item" key={item.id}>
                  <div className="repair-additional-header">
                    <h3 className="repair-popup-title">
                      추가 항목 {index + 1}
                    </h3>

                    <button
                      className="repair-additional-remove-button"
                      type="button"
                      onClick={() => handleRemoveAdditionalItem(item.id)}
                    >
                      삭제
                    </button>
                  </div>

                  <input
                    className="repair-additional-title-input"
                    type="text"
                    value={item.title}
                    placeholder="제목을 입력해 주세요"
                    onChange={(event) =>
                      handleAdditionalItemChange(
                        item.id,
                        "title",
                        event.target.value
                      )
                    }
                  />

                  <textarea
                    className="repair-additional-content-textarea"
                    value={item.content}
                    placeholder="내용을 입력해 주세요"
                    onChange={(event) =>
                      handleAdditionalItemChange(
                        item.id,
                        "content",
                        event.target.value
                      )
                    }
                  />
                </div>
              ))}
            </div>
          )}

          <button
            className="repair-add-button"
            type="button"
            onClick={handleAddItem}
          >
            +
          </button>
        </div>

        <div className="repair-popup-footer">
          <button
            className="repair-popup-cancel-button"
            type="button"
            onClick={onClose}
          >
            취소
          </button>

          <button
            className="repair-popup-complete-button"
            type="button"
            onClick={handleComplete}
            disabled={!isRequiredFilled}
          >
            완료
          </button>
        </div>
      </section>
    </div>
  );
}

export default RepairReportPopup;