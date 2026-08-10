import { useEffect, useState } from "react";
import {
  fetchWindFarmPower,
  fetchTurbinePower,
} from "../api/windFarmApi";
import PowerChartDrag from "./UnderBar/PowerChartDrag";

function UnderBar({
  mode,
  selectedPlant,
  selectedTurbine,
}) {
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
  const fetchPowerGeneration = async ({
    nextStartAt,
    nextEndAt,
  }) => {
    if (!selectedPlant?.id) {
      return;
    }

    try {
      setIsLoading(true);

      let responseBody;

      if (mode === "turbine") {
        if (!selectedTurbine?.id) {
          return;
        }

        responseBody = await fetchTurbinePower({
          turbineId: selectedTurbine.id,
          startTime: nextStartAt.toISOString(),
          endTime: nextEndAt.toISOString(),
          term: "HOURLY",
        });
      } else {
        if (!selectedPlant?.id) {
          return;
        }

        responseBody = await fetchWindFarmPower({
          windFarmId: selectedPlant.id,
          startTime: nextStartAt.toISOString(),
          endTime: nextEndAt.toISOString(),
          term: "HOURLY",
        });
      }

      const powerList = Array.isArray(responseBody)
        ? responseBody
        : Array.isArray(responseBody?.data)
          ? responseBody.data
          : [];

      const convertedPowerData = powerList.map((item) => ({
        measuredAt: item.time,
        powerGeneration: item.power ?? 0,
      }));

      setPowerData(convertedPowerData);
    } catch (error) {
      console.error(
        "발전량 조회 API 오류:",
        error
      );

      setPowerData([]);
    } finally {
      setIsLoading(false);
    }
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