function InspectionPeriodField({
  startDate,
  startTime,
  endDate,
  endTime,
  onChangeStartDate,
  onChangeStartTime,
  onChangeEndDate,
  onChangeEndTime,
}) {
  return (
    <div className="inspection-popup-section">
      <h3 className="inspection-popup-title">
        점검 기간 <span className="required-mark">*</span>
      </h3>

      <div className="inspection-date-row">
        <div className="inspection-date-field">
          <label>조회 기간 시작일</label>
          <input
            type="date"
            value={startDate}
            onChange={onChangeStartDate}
          />
        </div>

        <div className="inspection-date-field inspection-time-field">
          <label>시작 시간</label>
          <input
            type="time"
            value={startTime}
            onChange={onChangeStartTime}
          />
        </div>

        <span className="inspection-date-separator">~</span>

        <div className="inspection-date-field">
          <label>조회 기간 종료일</label>
          <input
            type="date"
            value={endDate}
            onChange={onChangeEndDate}
          />
        </div>

        <div className="inspection-date-field inspection-time-field">
          <label>종료 시간</label>
          <input
            type="time"
            value={endTime}
            onChange={onChangeEndTime}
          />
        </div>
      </div>
    </div>
  );
}

export default InspectionPeriodField;