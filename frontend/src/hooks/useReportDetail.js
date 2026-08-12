import { useEffect, useState } from "react";
import { fetchReportById } from "../api/reportApi";

export const useReportDetail = ({
  reportId,
} = {}) => {
  const [reportDetail, setReportDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (
      reportId === undefined ||
      reportId === null
    ) {
      setReportDetail(null);
      return;
    }

    let isMounted = true;

    const loadReportDetail = async () => {
      try {
        setLoading(true);
        setError(null);

        const responseBody =
          await fetchReportById(reportId);

        const responseData =
          responseBody?.data ??
          responseBody;

        if (!isMounted) {
          return;
        }

        setReportDetail(responseData);

        console.log(
          "보고서 상세:",
          responseData
        );
      } catch (error) {
        console.error(
          "보고서 상세 조회 에러:",
          error
        );

        if (isMounted) {
          setError(error.message);
          setReportDetail(null);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    loadReportDetail();

    return () => {
      isMounted = false;
    };
  }, [reportId, refreshKey]);

    const refetch = () => {
    setRefreshKey((prev) => prev + 1);
  };


  return {
    reportDetail,
    loading,
    error,
    refetch,
  };
};