import { useEffect, useState } from "react";

import { fetchWindFarms } from "../api/windFarmApi";
import { dummyWindFarms } from "../mocks/dummyWindFarms";
import { convertWindFarmToPlant } from "../utils/windFarmMapper";

const getWindFarmListFromResponse = (responseBody) => {
  if (Array.isArray(responseBody)) {
    return responseBody;
  }

  if (Array.isArray(responseBody.data)) {
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

        if (USE_DUMMY_WIND_FARMS) {
          const convertedDummyPlants =
            dummyWindFarms.map(convertWindFarmToPlant);

          if (isMounted) {
            setPlants(convertedDummyPlants);
          }

          return;
        }

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

    loadWindFarms(true);

    let intervalId = null;

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