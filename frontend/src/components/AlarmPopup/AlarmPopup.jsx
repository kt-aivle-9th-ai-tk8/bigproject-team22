import React, { useEffect, useState } from "react";
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
  onDeleteNotification,
}) {
  const navigate = useNavigate();

  const [isDeleteMode, setIsDeleteMode] = useState(false);
  const [ selectedNotificationIds, setSelectedNotificationIds ] = useState([]);
  const [displayAlarm, setDisplayAlarm] = useState(alarm);
  
  useEffect(() => {
    setDisplayAlarm(alarm);
  }, [alarm]);

  useEffect(() => {
    if (!isOpen) {
      setIsDeleteMode(false);
      setSelectedNotificationIds([]);
    }
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

  const handleToggleNotification = (
    notificationId
  ) => {
    setSelectedNotificationIds((prev) => {
      if (prev.includes(notificationId)) {
        return prev.filter(
          (id) => id !== notificationId
        );
      }

      return [
        ...prev,
        notificationId,
      ];
    });
  };


  const handleDeleteClick = async () => {
    if (!isDeleteMode) {
      setIsDeleteMode(true);
      setSelectedNotificationIds([]);
      return;
    }

    if (
      selectedNotificationIds.length === 0
    ) {
      alert(
        "삭제할 알림을 선택해 주세요."
      );
      return;
    }

    const isConfirmed = window.confirm(
      `선택한 알림 ${selectedNotificationIds.length}개를 삭제하시겠습니까?`
    );

    if (!isConfirmed) {
      return;
    }

    try {
      await Promise.all(
        selectedNotificationIds.map(
          (notificationId) =>
            onDeleteNotification?.(
              notificationId
            )
        )
      );

      setDisplayAlarm((prev) =>
        prev.filter(
          (report) =>
            !selectedNotificationIds.includes(
              report.id
            )
        )
      );

      setSelectedNotificationIds([]);
      setIsDeleteMode(false);
    } catch (error) {
      console.error(
        "알림 삭제 실패:",
        error
      );

      alert(
        "알림 삭제에 실패했습니다."
      );
    }
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

          <div className="alarm-popup-header-actions">
            <button
              className="alarm-delete-button"
              type="button"
              onClick={handleDeleteClick}
            >
              {isDeleteMode
                ? "완료"
                : "삭제"}
            </button>

            <button
              className="alarm-close-button"
              type="button"
              aria-label="알림 팝업 닫기"
              onClick={onClose}
            >
              ×
            </button>
          </div>
        </div>

        <AlarmReportList
          alarm={displayAlarm}
          onSelectReport={
            handleReportClick
          }
          isDeleteMode={isDeleteMode}
          selectedNotificationIds={
            selectedNotificationIds
          }
          onToggleNotification={
            handleToggleNotification
          }
        />
      </section>
    </div>
  );
}

function AlarmReportList({
  alarm,
  onSelectReport,
  isDeleteMode,
  selectedNotificationIds,
  onToggleNotification,
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
      {alarm.map((report) => {
        const isChecked =
          selectedNotificationIds.includes(
            report.id
          );

        const handleItemClick = () => {
          if (isDeleteMode) {
            onToggleNotification(
              report.id
            );

            return;
          }

          onSelectReport(report);
        };

        return (
          <div
            className={`alarm-list-item ${
              isDeleteMode
                ? "delete-mode"
                : ""
            }`}
            key={report.id}
            role="button"
            tabIndex={0}
            onClick={handleItemClick}
            onKeyDown={(event) => {
              if (
                event.key === "Enter" ||
                event.key === " "
              ) {
                handleItemClick();
              }
            }}
          >
            {isDeleteMode && (
              <input
                className="alarm-list-checkbox"
                type="checkbox"
                checked={isChecked}
                onChange={() => {}}
                onClick={(event) => {
                  event.stopPropagation();

                  onToggleNotification(
                    report.id
                  );
                }}
              />
            )}

            <div className="alarm-list-content">
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
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default AlarmPopup;