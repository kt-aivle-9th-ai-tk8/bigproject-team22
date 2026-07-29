import { useEffect, useState } from "react";
import PowerChartDrag from "./UnderBar/PowerChartDrag";

function createDummyPowerData(startAt, endAt) {
  const startTime = startAt.getTime();
  const endTime = endAt.getTime();

  const twoHourMs = 2 * 60 * 60 * 1000;
  const count = Math.max(1, Math.floor((endTime - startTime) / twoHourMs) + 1);

  return Array.from({ length: count }, (_, index) => {
    const measuredAt = new Date(startTime + index * twoHourMs);

    return {
      measuredAt: measuredAt.toISOString(),
      powerGeneration: Math.floor(100 + Math.random() * 500),
    };
  });
}

function UnderBar({ selectedPlant }) {
  const [axisStartAt, setAxisStartAt] = useState(null);
  const [axisEndAt, setAxisEndAt] = useState(null);

  const [powerData, setPowerData] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  /*
   * 1. 전체 x축 범위 조회
   * 예: 백엔드가 6월 1일 00시 ~ 현재 시간 반환
   */
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

  /*
   * 2. 현재 보이는 x축 구간의 발전량 조회
   */
  const fetchPowerGeneration = async ({ nextStartAt, nextEndAt }) => {
    setIsLoading(true);

    /*
     * 실제 API 예시:
     *
     * const query = new URLSearchParams({
     *   startAt: nextStartAt.toISOString(),
     *   endAt: nextEndAt.toISOString(),
     * });
     *
     * const response = await fetch(
     *   `/api/plants/${selectedPlant.id}/power-generation?${query.toString()}`
     * );
     *
     * const data = await response.json();
     * setPowerData(data);
     */

    setTimeout(() => {
      const dummyData = createDummyPowerData(nextStartAt, nextEndAt);

      setPowerData(dummyData);
      setIsLoading(false);
    }, 300);
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

    fetchPowerGeneration({
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