import { useEffect, useRef, useState } from "react";
import PowerChartDrag from "./UnderBar/PowerChartDrag";


function UnderBar({
  mode,
  selectedPlant,
  selectedTurbine,
  powerData,
  isLoading,
  powerError,
  onFetchPowerGeneration,
}) {
  const [axisStartAt, setAxisStartAt] = useState(null);
  const [axisEndAt, setAxisEndAt] = useState(null);

  const lastVisibleRangeRef = useRef(null);


  const fetchAxisRange = async () => {
    setAxisStartAt("2026-06-01T00:00:00");
    setAxisEndAt(new Date().toISOString());
  };


  useEffect(() => {
    fetchAxisRange();
  }, [selectedPlant?.id]);


  const handleRangeDrag = ({
    nextStartAt,
    nextEndAt,
    visibleTicks,
    visibleTickCount,
  }) => {
    


    lastVisibleRangeRef.current = {
      nextStartAt,
      nextEndAt,
    };


    onFetchPowerGeneration?.({
      nextStartAt,
      nextEndAt,
    });
  };


  useEffect(() => {
    const currentRange = lastVisibleRangeRef.current;

    if (!currentRange) {
      return;
    }

    if (mode === "turbine") {
      if (!selectedTurbine?.id) {
        return;
      }
    } else {
      if (!selectedPlant?.id) {
        return;
      }
    }

    onFetchPowerGeneration?.({
      nextStartAt: currentRange.nextStartAt,
      nextEndAt: currentRange.nextEndAt,
    });
  }, [
    mode,
    selectedPlant?.id,
    selectedTurbine?.id,
  ]);


  return (
    <section className="under-bar">
      <PowerChartDrag
        data={powerData}
        axisStartAt={axisStartAt}
        axisEndAt={axisEndAt}
        visibleTickCount={10}
        minVisibleTickCount={4}
        maxVisibleTickCount={30}
        isLoading={isLoading}
        onRangeDrag={handleRangeDrag}
        mode={mode}
        selectedPlant={selectedPlant}
        selectedTurbine={selectedTurbine}
      />
    </section>
  );
}


export default UnderBar;