import React, { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import "./AlarmPopup.css";


const formatSentAt = (sentAt) => {
  if (!sentAt) {
    return "";
  }

  const [date, time] = sentAt.split("T");

  return `${date.replaceAll("-", ".")}  ${time?.slice(0, 5) || ""}`;
};


function AlarmPopup({
  alarm = [],
  isOpen,
  onClose,
  onReadNotification,
}) {
  const navigate = useNavigate();


  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener(
      "keydown",
      handleKeyDown
    );

    return () => {
      window.removeEventListener(
        "keydown",
        handleKeyDown
      );
    };
  }, [isOpen, onClose]);


  if (!isOpen) {
    return null;
  }


  const handleOverlayClick = () => {
    onClose();
  };


  const handlePopupClick = (event) => {
    event.stopPropagation();
  };


  const handleReportClick = async (
    report
  ) => {
    const notificationId =
      report?.id;

    const reportId =
      report?.report_id ||
      report?.id;

    if (!reportId) {
      console.error(
        "이동할 보고서 ID가 없습니다.",
        report
      );

      return;
    }

    if (notificationId) {
      try {
        await onReadNotification?.(
          notificationId
        );
      } catch (error) {
        console.error(
          "알림 읽음 처리 실패:",
          error
        );
      }
    }

    onClose();

    navigate(
      `/reports/${reportId}/edit`
    );
  };


  return (
    <div
      className="alarm-overlay"
      role="presentation"
      onClick={handleOverlayClick}
    >
      <section
        className="alarm-popup"
        role="dialog"
        aria-modal="true"
        aria-labelledby="alarm-popup-title"
        onClick={handlePopupClick}
      >
        <div className="alarm-popup-header">
          <h2 id="alarm-popup-title">
            알림 보고서 리스트
          </h2>

          <button
            className="alarm-close-button"
            type="button"
            aria-label="알림 팝업 닫기"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <AlarmReportList
          alarm={alarm}
          onSelectReport={
            handleReportClick
          }
        />
      </section>
    </div>
  );
}


function AlarmReportList({
  alarm,
  onSelectReport,
}) {
  if (alarm.length === 0) {
    return (
      <div className="alarm-empty">
        등록된 알림이 없습니다.
      </div>
    );
  }

  return (
    <div className="alarm-list">
      {alarm.map((report) => (
        <button
          className="alarm-list-item"
          key={report.id}
          type="button"
          onClick={() =>
            onSelectReport(report)
          }
        >
          <div className="alarm-list-main">
            <span className="alarm-title">
              {report.report_title}
            </span>
          </div>

          <div className="alarm-list-sub">
            {formatSentAt(
              report.sent_at
            )}
          </div>
        </button>
      ))}
    </div>
  );
}


export default AlarmPopup;