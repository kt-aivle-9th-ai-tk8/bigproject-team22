import { useEffect, useState } from "react";

import { fetchWindFarms } from "../api/windFarmApi";


export const useReportWindFarms = () => {
  const [windFarms, setWindFarms] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);


  useEffect(() => {
    let isMounted = true;


    const loadWindFarms = async () => {
      try {
        setIsLoading(true);
        setError(null);


        const responseBody =
          await fetchWindFarms();


        const responseData =
          responseBody?.data ??
          responseBody;


        const windFarmList =
          Array.isArray(responseData)
            ? responseData
            : Array.isArray(responseData?.content)
              ? responseData.content
              : [];


        if (!isMounted) {
          return;
        }


        setWindFarms(windFarmList);


        console.log(
          "보고서 필터 발전소 목록:",
          windFarmList
        );
      } catch (error) {
        console.error(
          "발전소 목록 조회 오류:",
          error
        );


        if (isMounted) {
          setError(error.message);
          setWindFarms([]);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };


    loadWindFarms();


    return () => {
      isMounted = false;
    };
  }, []);


  return {
    windFarms,
    isLoading,
    error,
  };
};