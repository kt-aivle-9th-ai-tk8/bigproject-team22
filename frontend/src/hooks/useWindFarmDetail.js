import { useEffect, useState } from "react";

import { fetchWindFarmById } from "../api/windFarmApi";

export const useWindFarmDetail = ({
  mode,
  windFarmId,
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

    const loadWindFarmDetail = async () => {
      try {
        setIsWindFarmDetailLoading(true);
        setWindFarmDetailError(null);

        const responseBody =
          await fetchWindFarmById(windFarmId);

        if (isMounted) {
          setWindFarmDetail(responseBody);
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

    loadWindFarmDetail();

    return () => {
      isMounted = false;
    };
  }, [mode, windFarmId]);

  return {
    windFarmDetail,
    isWindFarmDetailLoading,
    windFarmDetailError,
  };
};