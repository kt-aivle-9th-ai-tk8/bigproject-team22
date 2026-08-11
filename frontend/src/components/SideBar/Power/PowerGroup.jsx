import PowerItem from "./PowerItem";
import "./PowerGroup.css";

function PowerGroup({ items, onSelectPlant }) {
  return (
    <div
      className="power-group"
      style={{ "--power-count": items.length }}
    >
      {items.map((item) => (
        <PowerItem
          key={item.id}
          title={item.title}
          currentOutput={item.currentOutput}
          currentPower={item.currentPower}
          monthPower={item.monthPower}
          onClick={() => onSelectPlant(item)}
        />
      ))}
    </div>
  );
}

export default PowerGroup;