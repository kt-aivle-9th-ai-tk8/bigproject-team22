import { useState } from "react";

import {
  fetchWindFarmPower,
  fetchTurbinePower,
} from "../api/windFarmApi";

const formatLocalDateTime = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  const second = String(date.getSeconds()).padStart(2, "0");

  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
};

export const usePowerGeneration = ({
  mode,
  selectedPlant,
  selectedTurbine,
} = {}) => {
  const [powerData, setPowerData] = useState([]);
  const [isPowerLoading, setIsPowerLoading] = useState(false);
  const [powerError, setPowerError] = useState(null);

  const fetchPowerGeneration = async ({
    nextStartAt,
    nextEndAt,
    term = "HOURLY",
  }) => {
    try {
      setIsPowerLoading(true);
      setPowerError(null);

      let responseBody;

      if (mode === "turbine") {
        if (!selectedTurbine?.id) {
          return;
        }

        responseBody = await fetchTurbinePower({
          turbineId: selectedTurbine.id,
          startTime: formatLocalDateTime(nextStartAt),
          endTime: formatLocalDateTime(nextEndAt),
          term,
        });
      } else {
        if (!selectedPlant?.id) {
          return;
        }

        responseBody = await fetchWindFarmPower({
          windFarmId: selectedPlant.id,
          startTime: formatLocalDateTime(nextStartAt),
          endTime: formatLocalDateTime(nextEndAt),
          term,
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
        isPowerGenerationNull: item.power == null,
      }));

      setPowerData(convertedPowerData);
    } catch (error) {
      console.error(
        "발전량 조회 API 오류:",
        error
      );

      setPowerError(error.message);
      setPowerData([]);
    } finally {
      setIsPowerLoading(false);
    }
  };

  return {
    powerData,
    isPowerLoading,
    powerError,
    fetchPowerGeneration,
  };
};