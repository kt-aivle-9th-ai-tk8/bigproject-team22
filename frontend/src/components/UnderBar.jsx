import { useEffect, useState } from "react";
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

  const fetchAxisRange = async () => {
    /*
     * 실제 API 예시:
     *
     * const response = await fetch(
     *   `/api/plants/${selectedPlant.id}/power-generation/range`
     * );
     *
     * const data = await response.json();
     *
     * setAxisStartAt(data.startAt);
     * setAxisEndAt(data.endAt);
     */

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
    console.log("현재 화면 x축 구간:", {
      nextStartAt: nextStartAt.toISOString(),
      nextEndAt: nextEndAt.toISOString(),
      visibleTickCount,
      visibleTicks: visibleTicks.map((tick) =>
        new Date(tick).toISOString()
      ),
    });

    onFetchPowerGeneration?.({
      nextStartAt,
      nextEndAt,
    });
  };

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
      />
    </section>
  );
}

export default UnderBar;