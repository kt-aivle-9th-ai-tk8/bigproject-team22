import FaultItem from "./FaultItem";
import "./FaultList.css";

function FaultList({ 
  items = [],
  onSelectNotification,
}) {
  return (
    <div className="fault-list">
      {items.map((item) => (
        <FaultItem
          key={item.id}
          notificationId={item.id}
          reportId={item.report_id}
          report_title={item.report_title}
          sent_at={item.sent_at}
          onSelectNotification={
            onSelectNotification
          }
        />
      ))}
    </div>
  );
}

export default FaultList;