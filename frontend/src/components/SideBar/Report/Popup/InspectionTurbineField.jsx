function InspectionTurbineField({
  turbineOptions,
  turbines,
  isFixedTurbine,
  onChangeTurbine,
}) {
  return (
    <div className="inspection-popup-section">
      <h3 className="inspection-popup-title">
        터빈 <span className="required-mark">*</span>
      </h3>

      <div className="inspection-turbine-list">
        {turbineOptions.map((turbineName) => (
          <label
            className="inspection-turbine-option"
            key={turbineName}
          >
            <input
              type="checkbox"
              checked={turbines.includes(turbineName)}
              disabled={isFixedTurbine}
              onChange={() => onChangeTurbine(turbineName)}
            />
            <span>{turbineName}</span>
          </label>
        ))}
      </div>
    </div>
  );
}

export default InspectionTurbineField;