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


        
      } catch (error) {
        


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