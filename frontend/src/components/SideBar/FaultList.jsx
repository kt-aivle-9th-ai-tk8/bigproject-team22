import FaultItem from "./FaultItem";
import "./FaultList.css";

function FaultList({ items }) {
  return (
    <div className="fault-list">
      {items.map((item, index) => (
        <FaultItem
          key={`${item.plantName}-${index}`}
          plantName={item.plantName}
          date={item.date}
          time={item.time}
          status={item.status}
        />
      ))}
    </div>
  );
}

export default FaultList;