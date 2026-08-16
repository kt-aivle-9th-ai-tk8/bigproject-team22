import {
  useCallback,
  useEffect,
  useState,
} from "react";

import {
  fetchWindFarms,
} from "../api/windFarmApi";

import {
  convertWindFarmToPlant,
} from "../utils/windFarmMapper";

const getWindFarmListFromResponse = (
  responseBody
) => {
  if (Array.isArray(responseBody)) {
    return responseBody;
  }

  if (Array.isArray(responseBody?.data)) {
    return responseBody.data;
  }

  return [];
};

export const useWindFarmOptions = () => {
  const [windFarms, setWindFarms] =
    useState([]);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState(null);

  const loadWindFarms =
    useCallback(async () => {
      try {
        setLoading(true);
        setError(null);

        const responseBody =
          await fetchWindFarms({
            location: 1,
          });

        const windFarmList =
          getWindFarmListFromResponse(
            responseBody
          );

        const convertedPlants =
          windFarmList.map(
            convertWindFarmToPlant
          );

        const windFarmOptions =
          convertedPlants.map(
            (plant) => ({
              id: plant.id,
              name: plant.name,
            })
          );

        console.log(
          "관리자용 발전소 목록:",
          windFarmOptions
        );

        setWindFarms(
          windFarmOptions
        );
      } catch (error) {
        console.error(
          "발전소 목록 조회 에러:",
          error
        );

        setError(error.message);
        setWindFarms([]);
      } finally {
        setLoading(false);
      }
    }, []);

  useEffect(() => {
    loadWindFarms();
  }, [loadWindFarms]);

  return {
    windFarms,
    loading,
    error,
    refetch: loadWindFarms,
  };
};