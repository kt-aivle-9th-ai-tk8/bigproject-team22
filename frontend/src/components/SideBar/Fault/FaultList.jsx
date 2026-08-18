import FaultItem from "./FaultItem";
import "./FaultList.css";

function FaultList({ items = [] }) {
  return (
    <div className="fault-list">
      {items.map((item) => (
        <FaultItem
          key={item.id}
          report_title={item.report_title}
          sent_at={item.sent_at}
        />
      ))}
    </div>
  );
}

export default FaultList;