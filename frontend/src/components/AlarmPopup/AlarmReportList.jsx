import "./AlarmReportList.css";


const formatSentAt = (sentAt) => {
  if (!sentAt) {
    return "";
  }

  const [date, time] = sentAt.split("T");

  return `${date.replaceAll("-", ".")}  ${
    time?.slice(0, 5) || ""
  }`;
};


function AlarmReportList({
  alarm = [],
  onSelectReport,
  isDeleteMode,
  selectedNotificationIds = [],
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


export default AlarmReportList;