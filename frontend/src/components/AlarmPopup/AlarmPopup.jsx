import React, { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";

import "./AlarmPopup.css";

function AlarmPopup({ alarm = [], isOpen, onClose }) {
  const [selectedReport, setSelectedReport] = useState(null);

  useEffect(() => {
    if (!isOpen) {
      setSelectedReport(null);
      return;
    }

    if (alarm.length === 1) {
      setSelectedReport(alarm[0]);
    } else {
      setSelectedReport(null);
    }
  }, [isOpen, alarm]);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
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

  const handleReportClick = (report) => {
    setSelectedReport(report);
  };

  const handleBackClick = () => {
    setSelectedReport(null);
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
            {selectedReport?.title ?? "알림 보고서 리스트"}
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

        {selectedReport ? (
          <AlarmReportDetail
            report={selectedReport}
            onBack={handleBackClick}
            showBackButton={alarm.length > 1}
          />
        ) : (
          <AlarmReportList
            alarm={alarm}
            onSelectReport={handleReportClick}
          />
        )}
      </section>
    </div>
  );
}

function AlarmReportList({ alarm, onSelectReport }) {
  if (alarm.length === 0) {
    return <div className="alarm-empty">등록된 알림이 없습니다.</div>;
  }

  return (
    <div className="alarm-list">
      {alarm.map((report) => (
        <button
          className="alarm-list-item"
          key={report.id}
          type="button"
          onClick={() => onSelectReport(report)}
        >
          <div className="alarm-list-main">
            <span className="alarm-title">{report.title}</span>
          </div>

          <div className="alarm-list-sub">
            {report.plantName} · {report.turbineName} · {report.time}
          </div>
        </button>
      ))}
    </div>
  );
}

function AlarmReportDetail({ report, onBack, showBackButton = true }) {
  return (
    <div className="alarm-report-detail">
      {showBackButton && (
        <button
          className="alarm-back-button"
          type="button"
          onClick={onBack}
        >
          ← 목록으로
        </button>
      )}

      <div className="alarm-report-markdown">
        <ReactMarkdown>{report.markdown ?? ""}</ReactMarkdown>
      </div>
    </div>
  );
}

export default AlarmPopup;