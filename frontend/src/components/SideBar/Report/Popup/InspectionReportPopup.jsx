import { useState } from "react";

import InspectionArchiveUploader from "./InspectionArchiveUploader";
import InspectionPeriodField from "./InspectionPeriodField";
import InspectionTurbineField from "./InspectionTurbineField";

import "./InspectionReportPopup.css";

const getTodayDate = () => {
  return new Date().toISOString().slice(0, 10);
};

const BLADE_IDS = ["Blade_A", "Blade_B", "Blade_C"];
const SURFACE_IDS = ["LE", "PS", "SS", "TE"];

function InspectionReportPopup({
  initialData = {},
  turbineOptions = ["터빈 A", "터빈 B", "터빈 C", "터빈 D"],
  onClose,
  onComplete,
}) {
  const today = getTodayDate();

  const [startDate, setStartDate] = useState(
    initialData.startDate || today
  );

  const [startTime, setStartTime] = useState(
    initialData.startTime || "00:00"
  );

  const [endDate, setEndDate] = useState(
    initialData.endDate || today
  );

  const [endTime, setEndTime] = useState(
    initialData.endTime || "23:59"
  );

  const [turbines, setTurbines] = useState(initialData.turbines || []);
  const [content, setContent] = useState(initialData.content || "");

  const [selectedFiles, setSelectedFiles] = useState(
    initialData.filesBySurface || {}
  );

  const isFixedTurbine = Boolean(initialData.fixedTurbine);

  const isAllImageSlotsFilled = () => {
    if (turbines.length === 0) return false;

    return turbines.every((turbineName) =>
      BLADE_IDS.every((bladeId) =>
        SURFACE_IDS.every((surfaceId) => {
          const fileKey = `${turbineName}-${bladeId}-${surfaceId}`;
          const images = selectedFiles[fileKey]?.images || [];

          return images.length > 0;
        })
      )
    );
  };

  const getDateTimeValue = (date, time) => {
    if (!date || !time) return "";
    return `${date}T${time}`;
  };

  const handleStartDateChange = (event) => {
    setStartDate(event.target.value);
  };

  const handleStartTimeChange = (event) => {
    setStartTime(event.target.value);
  };

  const handleEndDateChange = (event) => {
    setEndDate(event.target.value);
  };

  const handleEndTimeChange = (event) => {
    setEndTime(event.target.value);
  };

  const handleTurbineChange = (turbineName) => {
    if (isFixedTurbine) return;

    setTurbines((prev) => {
      if (prev.includes(turbineName)) {
        return prev.filter((item) => item !== turbineName);
      }

      return [...prev, turbineName];
    });
  };

  const handleComplete = () => {
    if (!startDate || !startTime || !endDate || !endTime) {
      alert("점검기간을 입력해주세요.");
      return;
    }

    if (turbines.length === 0) {
      alert("터빈을 선택해주세요.");
      return;
    }

    if (!isAllImageSlotsFilled()) {
      alert("모든 블레이드의 모든 면에 이미지를 업로드해주세요.");
      return;
    }

    const startDateTime = getDateTimeValue(startDate, startTime);
    const endDateTime = getDateTimeValue(endDate, endTime);

    if (startDateTime > endDateTime) {
      alert("종료일시는 시작일시보다 빠를 수 없습니다.");
      return;
    }

    const files = Object.values(selectedFiles);

    const popupData = {
      reportKind: "inspection",
      startDate,
      startTime,
      endDate,
      endTime,
      startDateTime,
      endDateTime,
      turbines,
      turbineName: turbines.join(", "),
      content,
      files,
      filesBySurface: selectedFiles,
    };

    
    onComplete?.(popupData);
  };

  return (
    <div
      className="inspection-popup-overlay"
      role="presentation"
      onClick={onClose}
    >
      <section
        className="inspection-popup"
        role="dialog"
        aria-modal="true"
        aria-label="점검 보고서 정보 입력"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="inspection-popup-body">
          <h2 className="inspection-popup-main-title">점검 보고서</h2>

          <InspectionPeriodField
            startDate={startDate}
            startTime={startTime}
            endDate={endDate}
            endTime={endTime}
            onChangeStartDate={handleStartDateChange}
            onChangeStartTime={handleStartTimeChange}
            onChangeEndDate={handleEndDateChange}
            onChangeEndTime={handleEndTimeChange}
          />

          <InspectionTurbineField
            turbineOptions={turbineOptions.map((turbine) =>
              typeof turbine === "string"
                ? turbine
                : turbine.name || turbine.code
            )}
            turbines={turbines}
            isFixedTurbine={isFixedTurbine}
            onChangeTurbine={handleTurbineChange}
          />

          {turbines.length > 0 && (
            <div className="inspection-popup-section">
              <h3 className="inspection-popup-title">
                드론 이미지 파일 <span className="required-mark">*</span>
              </h3>

              <InspectionArchiveUploader
                turbineOptions={turbines}
                selectedFiles={selectedFiles}
                onChangeFiles={setSelectedFiles}
              />
            </div>
          )}
        </div>

        <div className="inspection-popup-footer">
          <button
            className="inspection-popup-cancel-button"
            type="button"
            onClick={onClose}
          >
            취소
          </button>

          <button
            className="inspection-popup-complete-button"
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

export default InspectionReportPopup;