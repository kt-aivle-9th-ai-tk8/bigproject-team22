import "./FaultItem.css";

function FaultItem({
  report_title = "알림 보고서",
  sent_at = "",
}) {
  const formatSentAt = (sentAt) => {
    if (!sentAt) {
      return {
        date: "",
        time: "",
      };
    }

    const [date, time] = sentAt.split("T");

    return {
      date: date?.replaceAll("-", ".") || "",
      time: time?.slice(0, 5) || "",
    };
  };

  const formatReportTitle = (title) => {
    if (!title) {
      return "알림 보고서";
    }

    return title
      .replace(
        /\s*\d{4}-\d{2}-\d{2}.*$/,
        ""
      )
      .trim();
  };

  const { date, time } =
    formatSentAt(sent_at);

  const displayTitle =
    formatReportTitle(report_title);

  return (
    <div className="fault-item">
      <div className="fault-item-left">
        <div className="fault-item-icon">
          🚨
        </div>

        <div className="fault-item-name">
          {displayTitle}
        </div>
      </div>

      <div className="fault-item-time">
        {date}, {time}
      </div>
    </div>
  );
}

export default FaultItem;