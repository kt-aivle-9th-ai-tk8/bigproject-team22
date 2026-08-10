import { useEffect, useState } from "react";

import { fetchWindFarmById } from "../api/windFarmApi";
import { convertWindFarmDetailToPlant } from "../utils/windFarmMapper";

export const useWindFarmDetail = ({
  mode,
  windFarmId,
  refreshInterval = 600000,
} = {}) => {
  const [windFarmDetail, setWindFarmDetail] = useState(null);
  const [isWindFarmDetailLoading, setIsWindFarmDetailLoading] =
    useState(false);
  const [windFarmDetailError, setWindFarmDetailError] =
    useState(null);

  useEffect(() => {
    if (mode !== "plant" || !windFarmId) {
      return;
    }

    let isMounted = true;

    // plant 진입 시 전체 상세 정보 최초 조회
    const loadWindFarmDetail = async () => {
      try {
        setIsWindFarmDetailLoading(true);
        setWindFarmDetailError(null);

        const responseBody =
          await fetchWindFarmById(windFarmId);

        const convertedWindFarm =
          convertWindFarmDetailToPlant(responseBody.data);

        if (isMounted) {
          setWindFarmDetail(convertedWindFarm);
        }
      } catch (error) {
        console.error(
          "발전소 상세 조회 API 오류:",
          error
        );

        if (isMounted) {
          setWindFarmDetailError(error.message);
        }
      } finally {
        if (isMounted) {
          setIsWindFarmDetailLoading(false);
        }
      }
    };

    // 10분 갱신 시 weather + power만 변경
    const refreshWindFarmDetail = async () => {
      try {
        const responseBody =
          await fetchWindFarmById(windFarmId);

        const convertedWindFarm =
          convertWindFarmDetailToPlant(responseBody.data);

        if (isMounted) {
          setWindFarmDetail((prevDetail) => {
            if (!prevDetail) {
              return convertedWindFarm;
            }

            return {
              ...prevDetail,
              weather: convertedWindFarm.weather,
              power: convertedWindFarm.power,
            };
          });
        }
      } catch (error) {
        console.error(
          "발전소 상세 갱신 API 오류:",
          error
        );

        if (isMounted) {
          setWindFarmDetailError(error.message);
        }
      }
    };

    // plant 진입 즉시 1회 조회
    loadWindFarmDetail();

    let intervalId = null;

    // 이후 10분마다 weather + power 갱신
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        refreshWindFarmDetail();
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [mode, windFarmId, refreshInterval]);

  return {
    windFarmDetail,
    isWindFarmDetailLoading,
    windFarmDetailError,
  };
};