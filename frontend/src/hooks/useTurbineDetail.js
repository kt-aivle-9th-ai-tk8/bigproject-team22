import { useEffect, useState } from "react";

import { fetchTurbineById } from "../api/turbineApi";

export const useTurbineDetail = ({
  mode,
  turbineId,
  refreshInterval = 600000,
} = {}) => {
  const [turbineDetail, setTurbineDetail] = useState(null);
  const [isTurbineDetailLoading, setIsTurbineDetailLoading] =
    useState(false);
  const [turbineDetailError, setTurbineDetailError] =
    useState(null);

  useEffect(() => {
    if (mode !== "turbine" || !turbineId) {
      return;
    }

    let isMounted = true;

    const loadTurbineDetail = async () => {
      try {
        setIsTurbineDetailLoading(true);
        setTurbineDetailError(null);

        const responseBody =
          await fetchTurbineById(turbineId);

        const turbine =
          responseBody?.data ?? responseBody;

        if (isMounted) {
          setTurbineDetail(turbine);
        }
      } catch (error) {
        console.error(
          "터빈 상세 조회 API 오류:",
          error
        );

        if (isMounted) {
          setTurbineDetailError(error.message);
        }
      } finally {
        if (isMounted) {
          setIsTurbineDetailLoading(false);
        }
      }
    };

    const refreshTurbinePower = async () => {
      try {
        const responseBody =
          await fetchTurbineById(turbineId);

        const turbine =
          responseBody?.data ?? responseBody;

        if (isMounted) {
          setTurbineDetail((prevDetail) => {
            if (!prevDetail) {
              return turbine;
            }

            return {
              ...prevDetail,
              power: turbine?.power ?? prevDetail.power,
            };
          });
        }
      } catch (error) {
        console.error(
          "터빈 출력 갱신 API 오류:",
          error
        );

        if (isMounted) {
          setTurbineDetailError(error.message);
        }
      }
    };

    loadTurbineDetail();

    let intervalId = null;

    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        refreshTurbinePower();
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [mode, turbineId, refreshInterval]);

  return {
    turbineDetail,
    isTurbineDetailLoading,
    turbineDetailError,
  };
};