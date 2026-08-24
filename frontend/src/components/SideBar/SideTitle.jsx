import "./SideTitle.css";

function SideTitle({
  children,
  leftContent,
}) {
  return (
    <div className="side-title">
      <div className="side-title-left">
        {leftContent}
      </div>

      <div className="side-title-text">
        {children}
      </div>
    </div>
  );
}

export default SideTitle;