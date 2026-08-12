import { useEffect, useState } from "react";

import { fetchReports } from "../api/reportApi";


export const useReportList = () => {
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
          await fetchReports();

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
        console.log(
          "전체 보고서 목록:",
          reportList
        );
      } catch (error) {
        console.error(
          "보고서 목록 조회 에러:",
          error
        );
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
  }, []);

  return {
    reports,
    loading,
    error,
  };
};