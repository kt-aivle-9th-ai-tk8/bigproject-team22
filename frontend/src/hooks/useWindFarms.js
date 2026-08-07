import { useEffect, useState } from "react";

import { fetchWindFarms } from "../api/windFarmApi";
import { convertWindFarmToPlant } from "../utils/windFarmMapper";

const getWindFarmListFromResponse = (responseBody) => {
  if (Array.isArray(responseBody)) {
    return responseBody;
  }

  if (Array.isArray(responseBody?.data)) {
    return responseBody.data;
  }

  return [];
};

export const useWindFarms = ({
  mode,
  refreshInterval = 5000,
  topN,
  location = 1,
  power = 1,
  weather = 1,
} = {}) => {
  const [plants, setPlants] = useState([]);
  const [isPlantsLoading, setIsPlantsLoading] = useState(true);
  const [plantsError, setPlantsError] = useState(null);

  useEffect(() => {
    if (mode !== "map") {
      return;
    }

    let isMounted = true;

    const loadWindFarms = async (isInitial = false) => {
      try {
        if (isInitial) {
          setIsPlantsLoading(true);
        }

        setPlantsError(null);

        const responseBody = await fetchWindFarms({
          topN,
          location,
          power,
          weather,
        });

        const windFarmList =
          getWindFarmListFromResponse(responseBody);

        const convertedPlants =
          windFarmList.map(convertWindFarmToPlant);

        if (isMounted) {
          setPlants(convertedPlants);
        }
      } catch (error) {
        console.error("발전소 목록 API 오류:", error);

        if (isMounted) {
          setPlantsError(error.message);
        }
      } finally {
        if (isInitial && isMounted) {
          setIsPlantsLoading(false);
        }
      }
    };

    // map 진입 시 즉시 1회 호출
    loadWindFarms(true);

    let intervalId = null;

    // 설정된 시간마다 다시 호출
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        loadWindFarms(false);
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [
    mode,
    refreshInterval,
    topN,
    location,
    power,
    weather,
  ]);

  return {
    plants,
    isPlantsLoading,
    plantsError,
    setPlants,
  };
};