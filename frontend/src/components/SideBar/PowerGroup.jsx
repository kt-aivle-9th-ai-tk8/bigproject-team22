import PowerItem from "./PowerItem";
import "./PowerGroup.css";

function PowerGroup({ items }) {
  return (
    <div
      className="power-group"
      style={{ "--power-count": items.length }}
    >
      {items.map((item) => (
        <PowerItem
          key={item.title}
          title={item.title}
          currentOutput={item.currentOutput}
          currentPower={item.currentPower}
          monthPower={item.monthPower}
          yearPower={item.yearPower}
        />
      ))}
    </div>
  );
}

export default PowerGroup;