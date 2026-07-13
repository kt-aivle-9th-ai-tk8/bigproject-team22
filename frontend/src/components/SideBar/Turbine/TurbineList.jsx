import TurbineItem from "./TurbineItem";
import "./TurbineList.css";

function TurbineList({ items, onSelectTurbine }) {
  return (
    <div className="turbine-list">
      {items.map((item) => (
        <TurbineItem
          key={item.id}
          name={item.name}
          status={item.status}
          alertCount={item.alertCount}
          hasEmergency={item.hasEmergency}
          onClick={onSelectTurbine ? () => onSelectTurbine(item) : undefined}
        />
      ))}
    </div>
  );
}

export default TurbineList;