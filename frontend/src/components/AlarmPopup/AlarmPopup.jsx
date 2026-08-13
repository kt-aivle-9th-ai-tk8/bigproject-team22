import React, { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";

import { useReportDetail } from "../../hooks/useReportDetail";

import "./AlarmPopup.css";

const formatSentAt = (sentAt) => {
  if (!sentAt) {
    return "";
  }

  const [date, time] = sentAt.split("T");

  return `${date.replaceAll("-", ".")}  ${time?.slice(0, 5) || ""}`;
};

function AlarmPopup({ alarm = [], isOpen, onClose }) {
  const [selectedReport, setSelectedReport] = useState(null);
  
  const selectedReportId =
    selectedReport?.report_id ||
    selectedReport?.id;

  const {
    reportDetail,
    loading: isReportDetailLoading,
  } = useReportDetail({
    reportId: selectedReportId,
  });
  
  useEffect(() => {
    if (!isOpen) {
      setSelectedReport(null);
      return;
    }

    setSelectedReport(null);
  }, [isOpen]);

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
            {selectedReport
              ? (
                  reportDetail?.title ||
                  selectedReport?.title ||
                  selectedReport?.report_title ||
                  "보고서"
                )
              : "알림 보고서 리스트"}
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
            reportDetail={reportDetail}
            isLoading={isReportDetailLoading}
            onBack={handleBackClick}
            showBackButton={true}
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
            <span className="alarm-title">
              {report.report_title}
            </span>
          </div>

          <div className="alarm-list-sub">
            {formatSentAt(report.sent_at)}
          </div>
        </button>
      ))}
    </div>
  );
}

function AlarmReportDetail({
  report,
  reportDetail,
  isLoading,
  onBack,
  showBackButton = true,
}) {
  const markdownContent =
    reportDetail?.context ||
    "";

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
        {isLoading ? (
          <div className="alarm-empty">
            보고서 내용을 불러오는 중입니다...
          </div>
        ) : markdownContent ? (
          <ReactMarkdown>
            {markdownContent}
          </ReactMarkdown>
        ) : (
          <div className="alarm-empty">
            표시할 보고서 내용이 없습니다.
          </div>
        )}
      </div>
    </div>
  );
}

export default AlarmPopup;