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

  const { date, time } = formatSentAt(sent_at);

  return (
    <div className="fault-item">
      <div className="fault-item-icon">
        🚨
      </div>

      <div className="fault-item-content">
        <div className="fault-item-plant-name">
          {report_title}
        </div>

        <div className="fault-item-time">
          {date}, {time}
        </div>
      </div>
    </div>
  );
}

export default FaultItem;