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
  refreshInterval = 600000,
} = {}) => {
  const [plants, setPlants] = useState([]);
  const [isPlantsLoading, setIsPlantsLoading] = useState(true);
  const [plantsError, setPlantsError] = useState(null);

  useEffect(() => {
    if (mode !== "map") {
      return;
    }

    let isMounted = true;

    // 최초 진입: 위치 + 발전량 + 날씨 전체 조회
    const loadInitialWindFarms = async () => {
      try {
        setIsPlantsLoading(true);
        setPlantsError(null);

        const responseBody = await fetchWindFarms({
          location: 1,
          power: 1,
          weather: 1,
        });

        const windFarmList =
          getWindFarmListFromResponse(responseBody);

        const convertedPlants =
          windFarmList.map(convertWindFarmToPlant);

        if (isMounted) {
          setPlants(convertedPlants);
        }
      } catch (error) {
        

        if (isMounted) {
          setPlantsError(error.message);
        }
      } finally {
        if (isMounted) {
          setIsPlantsLoading(false);
        }
      }
    };

    // 갱신: 발전량 + 날씨만 조회
    const refreshWindFarms = async () => {
      try {
        const responseBody = await fetchWindFarms({
          power: 1,
          weather: 1,
        });

        const windFarmList =
          getWindFarmListFromResponse(responseBody);

        const refreshedPlants =
          windFarmList.map(convertWindFarmToPlant);

        if (isMounted) {
          setPlants((prevPlants) =>
            prevPlants.map((prevPlant) => {
              const refreshedPlant =
                refreshedPlants.find(
                  (plant) =>
                    plant.id === prevPlant.id
                );

              if (!refreshedPlant) {
                return prevPlant;
              }

              return {
                ...prevPlant,
                weather: refreshedPlant.weather,
                power: refreshedPlant.power,
              };
            })
          );
        }
      } catch (error) {
        

        if (isMounted) {
          setPlantsError(error.message);
        }
      }
    };

    // map 진입 시 즉시 전체 조회
    loadInitialWindFarms();

    let intervalId = null;

    // 이후 10분마다 power + weather 갱신
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        refreshWindFarms();
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [mode, refreshInterval]);

  return {
    plants,
    isPlantsLoading,
    plantsError,
    setPlants,
  };
};