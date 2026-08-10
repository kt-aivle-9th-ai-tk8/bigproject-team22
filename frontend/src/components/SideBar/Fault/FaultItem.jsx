// import "./FaultItem.css";

// export const FAULT_STATUS = {
//   ALERT: "ALERT",
//   WARNING: "WARNING",
// };

// const FAULT_ICON_MAP = {
//   [FAULT_STATUS.ALERT]: "🚨",
//   [FAULT_STATUS.WARNING]: "⚠️",
// };

// function FaultItem({
//   plantName = "장흥 발전소",
//   date = "07.08",
//   time = "12:00",
//   status = FAULT_STATUS.ALERT,
// }) {
//   const icon = FAULT_ICON_MAP[status] || FAULT_ICON_MAP[FAULT_STATUS.ALERT];

//   return (
//     <div className="fault-item">
//       <div className="fault-item-left">
//         <span className="fault-item-icon">{icon}</span>
//         <span className="fault-item-name">{plantName}</span>
//       </div>

//       <div className="fault-item-time">
//         {date}, {time}
//       </div>
//     </div>
//   );
// }

// export default FaultItem;
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