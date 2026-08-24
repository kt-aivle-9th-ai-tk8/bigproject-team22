import TurbineItem from "./TurbineItem";
import "./TurbineList.css";


function TurbineList({ items, onSelectTurbine }) {
  const statusPriority = {
    NO_DATA: 0,
    ZERO_POWER: 1,
    NORMAL: 2,
  };

  const sortedItems = [...items].sort((a, b) => {
    const statusCompare =
      (statusPriority[a.status] ?? 999) -
      (statusPriority[b.status] ?? 999);

    if (statusCompare !== 0) {
      return statusCompare;
    }

    return Number(a.id) - Number(b.id);
  });


  return (
    <div className="turbine-list">
      {sortedItems.map((item) => (
        <TurbineItem
          key={item.id}
          name={item.name}
          status={item.status}
          abnormalDetected={item.abnormalDetected}
          onClick={onSelectTurbine ? () => onSelectTurbine(item) : undefined}
        />
      ))}
    </div>
  );
}


export default TurbineList;


export const TURBINE_STATUS = {
  NORMAL: "NORMAL",
  ZERO_POWER: "ZERO_POWER",
  NO_DATA: "NO_DATA",
};