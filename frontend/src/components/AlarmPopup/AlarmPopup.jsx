import React, { useEffect, useState } from "react";

import AlarmReportList from "./AlarmReportList";

import "./AlarmPopup.css";


function AlarmPopup({
  alarm = [],
  isOpen,
  onClose,
  onReadNotification,
  onDeleteNotification,
}) {

  const [isDeleteMode, setIsDeleteMode] =
    useState(false);

  const [
    selectedNotificationIds,
    setSelectedNotificationIds,
  ] = useState([]);

  const [displayAlarm, setDisplayAlarm] =
    useState(alarm);


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

    if (!notificationId) {
      console.error(
        "알림 ID가 없습니다.",
        report
      );

      return;
    }

    if (!reportId) {
      console.error(
        "이동할 보고서 ID가 없습니다.",
        report
      );

      return;
    }

    onClose();

    await onReadNotification?.(
      notificationId,
      reportId
    );
  };


  const handleToggleNotification = (
    notificationId
  ) => {
    setSelectedNotificationIds((prev) => {
      if (
        prev.includes(notificationId)
      ) {
        return prev.filter(
          (id) =>
            id !== notificationId
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
          isDeleteMode={
            isDeleteMode
          }
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


export default AlarmPopup;