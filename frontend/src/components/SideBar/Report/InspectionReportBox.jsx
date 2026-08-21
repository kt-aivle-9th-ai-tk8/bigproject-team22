import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
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
  isCreating = false,
  onCreateReport,
}) {
  const navigate = useNavigate();

  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [inspectionInfo, setInspectionInfo] = useState({
    ...EMPTY_INSPECTION_INFO,
    ...initialData,
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

  const handleInfoClick = () => {
    setIsPopupOpen(true);
  };

  const handlePopupComplete = (popupData) => {
    

    setInspectionInfo(popupData);
    setIsPopupOpen(false);
  };

  const handleCreateReport = () => {
    const files =
      inspectionInfo.files ||
      Object.values(inspectionInfo.filesBySurface || {});

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

    const turbineIds = inspectionInfo.turbines
      .map((turbineName) => {
        const turbine = turbineOptions.find((option) => {
          if (typeof option === "string") {
            return false;
          }

          return (
            option.name === turbineName ||
            option.code === turbineName
          );
        });

        return turbine?.id;
      })
      .filter((id) => id !== undefined && id !== null);

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
      turbines: turbineIds,
      turbineNames: inspectionInfo.turbines,
      turbineName:
        inspectionInfo.turbines.length > 0
          ? inspectionInfo.turbines.join(", ")
          : turbineName,
      //content: inspectionInfo.content,
      additionalItems: inspectionInfo.additionalItems,
      files,
      filesBySurface: inspectionInfo.filesBySurface || {},
    };

    onCreateReport?.(reportData);

    setInspectionInfo({
      ...EMPTY_INSPECTION_INFO,
      ...initialData,
    });
  };

  const handleGoToReportList = () => {
    navigate("/reportlist");
  };

  const displayInspectionPeriod =
    inspectionInfo.startDate && inspectionInfo.endDate
      ? `${inspectionInfo.startDate} ${inspectionInfo.startTime} ~ ${inspectionInfo.endDate} ${inspectionInfo.endTime}`
      : inspectionPeriod;

  const displayTurbineName =
    inspectionInfo.turbines.length > 0
      ? inspectionInfo.turbines.join(", ")
      : turbineName;

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
          
        </button>

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