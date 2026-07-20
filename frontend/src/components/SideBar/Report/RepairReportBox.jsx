import { useState } from "react";
import RepairReportPopup from "./RepairReportPopup";
import "./RepairReportBox.css";

const EMPTY_REPAIR_INFO = {
  startDate: "",
  startTime: "00:00",
  endDate: "",
  endTime: "23:59",
  turbines: [],
  documentId: "",
  content: "",
  additionalItems: [],
};

function RepairReportBox({
  repairPeriod = "-",
  turbineName = "-",
  documentId = "-",
  turbineOptions = [],
  onCreateReport,
}) {
  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [repairInfo, setRepairInfo] = useState(EMPTY_REPAIR_INFO);

  const handleInfoClick = () => {
    setIsPopupOpen(true);
  };

  const handlePopupComplete = (popupData) => {
    setRepairInfo(popupData);
    setIsPopupOpen(false);
  };

  const handleCreateReport = () => {
    const reportData = {
      reportKind: "repair",
      repairPeriod:
        repairInfo.startDate && repairInfo.endDate
          ? `${repairInfo.startDate} ${repairInfo.startTime} ~ ${repairInfo.endDate} ${repairInfo.endTime}`
          : repairPeriod,
      startDate: repairInfo.startDate,
      startTime: repairInfo.startTime,
      endDate: repairInfo.endDate,
      endTime: repairInfo.endTime,
      startDateTime: repairInfo.startDateTime,
      endDateTime: repairInfo.endDateTime,
      turbines: repairInfo.turbines,
      turbineName:
        repairInfo.turbines.length > 0
          ? repairInfo.turbines.join(", ")
          : turbineName,
      documentId: repairInfo.documentId || documentId,
      content: repairInfo.content,
      additionalItems: repairInfo.additionalItems,
    };

    console.log("수리 보고서 생성 JSON:", reportData);
    onCreateReport?.(reportData);

    setRepairInfo(EMPTY_REPAIR_INFO);
  };

  const displayRepairPeriod =
    repairInfo.startDate && repairInfo.endDate
      ? `${repairInfo.startDate} ${repairInfo.startTime} ~ ${repairInfo.endDate} ${repairInfo.endTime}`
      : repairPeriod;

  const displayTurbineName =
    repairInfo.turbines.length > 0
      ? repairInfo.turbines.join(", ")
      : turbineName;

  const displayDocumentId = repairInfo.documentId || documentId;

  return (
    <>
      <div className="repair-report-box">
        <button
          className="repair-info-button"
          type="button"
          onClick={handleInfoClick}
        >
          <div className="repair-info-row">
            <span className="repair-info-label">수리기간</span>
            <span className="repair-info-value">{displayRepairPeriod}</span>
          </div>

          <div className="repair-info-row">
            <span className="repair-info-label">터빈</span>
            <span className="repair-info-value">{displayTurbineName}</span>
          </div>

          <div className="repair-info-row">
            <span className="repair-info-label">공문 ID</span>
            <span className="repair-info-value">{displayDocumentId}</span>
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
        <RepairReportPopup
          initialData={repairInfo}
          turbineOptions={turbineOptions}
          onClose={() => setIsPopupOpen(false)}
          onComplete={handlePopupComplete}
        />
      )}
    </>
  );
}

export default RepairReportBox;