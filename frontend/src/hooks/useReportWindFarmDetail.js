import { useEffect, useState } from "react";

import { fetchWindFarmById } from "../api/windFarmApi";


export const useReportWindFarmDetail = ({
  windFarmId,
} = {}) => {
  const [windFarmDetail, setWindFarmDetail] =
    useState(null);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState(null);


  useEffect(() => {
    let isMounted = true;


    const loadWindFarmDetail = async () => {
      if (
        windFarmId === undefined ||
        windFarmId === null ||
        windFarmId === "전체"
      ) {
        setWindFarmDetail(null);
        return;
      }


      try {
        setLoading(true);
        setError(null);


        const responseBody =
          await fetchWindFarmById(
            windFarmId
          );


        const responseData =
          responseBody?.data ??
          responseBody;


        if (!isMounted) {
          return;
        }


        setWindFarmDetail(
          responseData
        );


        console.log(
          "보고서 필터 발전소 상세:",
          responseData
        );
      } catch (error) {
        console.error(
          "발전소 상세 조회 오류:",
          error
        );


        if (isMounted) {
          setError(error.message);
          setWindFarmDetail(null);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };


    loadWindFarmDetail();


    return () => {
      isMounted = false;
    };
  }, [windFarmId]);


  return {
    windFarmDetail,
    loading,
    error,
  };
};