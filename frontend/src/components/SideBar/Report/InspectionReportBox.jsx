import { useState } from "react";
import InspectionReportPopup from "./Popup/InspectionReportPopup";
import "./InspectionReportBox.css";

const EMPTY_INSPECTION_INFO = {
  startDate: "",
  startTime: "00:00",
  endDate: "",
  endTime: "23:59",
  turbines: [],
  content: "",
  additionalItems: [],
  files: [],
  filesBySurface: {},
};

function InspectionReportBox({
  inspectionPeriod = "-",
  turbineName = "-",
  turbineOptions = [],
  initialData = {},
  onCreateReport,
}) {
  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [inspectionInfo, setInspectionInfo] = useState({
    ...EMPTY_INSPECTION_INFO,
    ...initialData,
  });

  const handleInfoClick = () => {
    setIsPopupOpen(true);
  };

  const handlePopupComplete = (popupData) => {
    console.log("점검 보고서 팝업 완료 데이터:", popupData);

    setInspectionInfo(popupData);
    setIsPopupOpen(false);
  };

  const handleCreateReport = () => {
    const files = inspectionInfo.files || Object.values(
      inspectionInfo.filesBySurface || {}
    );

    const isEmptyInspectionInfo =
      !inspectionInfo.startDate ||
      !inspectionInfo.startTime ||
      !inspectionInfo.endDate ||
      !inspectionInfo.endTime ||
      inspectionInfo.turbines.length === 0 ||
      files.length === 0;

    if (isEmptyInspectionInfo) {
      alert("보고서 정보를 입력해주세요.");
      return;
    }

    const reportData = {
      reportKind: "inspection",
      inspectionPeriod:
        inspectionInfo.startDate && inspectionInfo.endDate
          ? `${inspectionInfo.startDate} ${inspectionInfo.startTime} ~ ${inspectionInfo.endDate} ${inspectionInfo.endTime}`
          : inspectionPeriod,
      startDate: inspectionInfo.startDate,
      startTime: inspectionInfo.startTime,
      endDate: inspectionInfo.endDate,
      endTime: inspectionInfo.endTime,
      startDateTime: inspectionInfo.startDateTime,
      endDateTime: inspectionInfo.endDateTime,
      turbines: inspectionInfo.turbines,
      turbineName:
        inspectionInfo.turbines.length > 0
          ? inspectionInfo.turbines.join(", ")
          : turbineName,
      content: inspectionInfo.content,
      additionalItems: inspectionInfo.additionalItems,
      files,
      filesBySurface: inspectionInfo.filesBySurface || {},
    };

    console.log("====================================");
    console.log("점검 보고서 생성 버튼 클릭");
    console.log("점검 보고서 생성 최종 데이터:", reportData);
    console.log("업로드 ZIP 파일 목록:", files);
    console.table(
      files.map((fileItem) => ({
        turbineName: fileItem.turbineName,
        bladeLabel: fileItem.bladeLabel,
        bladeId: fileItem.bladeId,
        surfaceLabel: fileItem.surfaceLabel,
        surfaceId: fileItem.surfaceId,
        fileName: fileItem.fileName,
        fileSize: fileItem.fileSize,
        fileType: fileItem.fileType,
      }))
    );
    console.log("====================================");

    onCreateReport?.(reportData);

    setInspectionInfo({
      ...EMPTY_INSPECTION_INFO,
      ...initialData,
    });
  };

  const displayInspectionPeriod =
    inspectionInfo.startDate && inspectionInfo.endDate
      ? `${inspectionInfo.startDate} ${inspectionInfo.startTime} ~ ${inspectionInfo.endDate} ${inspectionInfo.endTime}`
      : inspectionPeriod;

  const displayTurbineName =
    inspectionInfo.turbines.length > 0
      ? inspectionInfo.turbines.join(", ")
      : turbineName;

  const displayContent = inspectionInfo.content?.trim() || "-";

  return (
    <>
      <div className="inspection-report-box">
        <button
          className="inspection-info-button"
          type="button"
          onClick={handleInfoClick}
        >
          <div className="inspection-info-row">
            <span className="inspection-info-label">점검 기간</span>
            <span className="inspection-info-value">
              {displayInspectionPeriod}
            </span>
          </div>

          <div className="inspection-info-row">
            <span className="inspection-info-label">터빈</span>
            <span className="inspection-info-value">{displayTurbineName}</span>
          </div>

          <div className="inspection-info-row">
            <span className="inspection-info-label">추가 내용</span>
            <span className="inspection-info-value">{displayContent}</span>
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
        <InspectionReportPopup
          initialData={inspectionInfo}
          turbineOptions={turbineOptions}
          onClose={() => setIsPopupOpen(false)}
          onComplete={handlePopupComplete}
        />
      )}
    </>
  );
}

export default InspectionReportBox;