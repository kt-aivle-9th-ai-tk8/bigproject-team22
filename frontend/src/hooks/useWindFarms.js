import { useEffect, useState } from "react";

import { fetchWindFarms } from "../api/windFarmApi";
import { dummyWindFarms } from "../mocks/dummyWindFarms";
import { convertWindFarmToPlant } from "../utils/windFarmMapper";

const USE_DUMMY_WIND_FARMS = true;

const getWindFarmListFromResponse = (data) => {
  if (Array.isArray(data)) {
    return data;
  }

  return data.data || data.windFarms || [];
};

export const useWindFarms = ({
  topN,
  location = 1,
  power = 1,
  weather = 1,
} = {}) => {
  const [plants, setPlants] = useState([]);
  const [isPlantsLoading, setIsPlantsLoading] = useState(true);
  const [plantsError, setPlantsError] = useState(null);

  useEffect(() => {
    const loadWindFarms = async () => {
      try {
        setIsPlantsLoading(true);
        setPlantsError(null);

        if (USE_DUMMY_WIND_FARMS) {
          const convertedDummyPlants =
            dummyWindFarms.map(convertWindFarmToPlant);

          setPlants(convertedDummyPlants);
          return;
        }

        const data = await fetchWindFarms({
          topN,
          location,
          power,
          weather,
        });

        const windFarmList = getWindFarmListFromResponse(data);
        const convertedPlants =
          windFarmList.map(convertWindFarmToPlant);

        setPlants(convertedPlants);
      } catch (error) {
        console.error("발전소 목록 API 오류:", error);
        setPlantsError(error.message);
      } finally {
        setIsPlantsLoading(false);
      }
    };

    loadWindFarms();
  }, [topN, location, power, weather]);

  return {
    plants,
    isPlantsLoading,
    plantsError,
    setPlants,
  };
};