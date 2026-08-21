import { useEffect, useState } from "react";

import { fetchReports } from "../api/reportApi";


export const useReportList = ({
  windFarmId,
  turbineId,
  reportType,
} = {}) => {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;

    const loadReports = async () => {
      try {
        setLoading(true);
        setError(null);

        const responseBody =
          await fetchReports({
            windFarmId,
            turbineId,
            reportType,
          });

        const responseData =
          responseBody?.data ??
          responseBody;

        const reportList = (
            Array.isArray(responseData)
                ? responseData
                : Array.isArray(responseData?.content)
                ? responseData.content
                : []
            ).filter((report) => {
            const reportStatus = String(
                report?.status || ""
            ).toLowerCase();

            return reportStatus === "generated";
        });

        if (!isMounted) {
          return;
        }
        setReports(reportList)
        
      } catch (error) {
        
        if (isMounted) {
          setError(error.message);
          setReports([]);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    loadReports();

    return () => {
      isMounted = false;
    };
  }, [
    windFarmId,
    turbineId,
    reportType,
  ]);

  return {
    reports,
    loading,
    error,
  };
};