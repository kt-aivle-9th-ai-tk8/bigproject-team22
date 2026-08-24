import { useEffect, useRef, useState } from "react";

import {
  createReport,
  fetchReportById,
} from "../api/reportApi";

export const useReportStatusPolling = ({
  refreshInterval = 5000,
  onGenerated,
} = {}) => {
  const [reportId, setReportId] = useState(null);
  const [reportDetail, setReportDetail] = useState(null);

  const [isReportCreating, setIsReportCreating] =
    useState(false);

  const [reportStatusError, setReportStatusError] =
    useState(null);

  const onGeneratedRef = useRef(onGenerated);

  useEffect(() => {
    onGeneratedRef.current = onGenerated;
  }, [onGenerated]);

  const createOperationReport = async ({
    windFarmId,
    turbineId,
    periodStart,
    periodEnd,
    reportType,
  }) => {
    try {
      setIsReportCreating(true);
      setReportStatusError(null);

      const responseBody = await createReport({
        windFarmId,
        turbineId,
        periodStart,
        periodEnd,
        reportType,
      });

      const createdReportId =
        responseBody?.data?.id ??
        responseBody?.id;

      if (!createdReportId) {
        throw new Error(
          "생성된 보고서 ID를 확인할 수 없습니다."
        );
      }

      

      setReportDetail(null);
      setReportId(createdReportId);

      return responseBody;
    } catch (error) {
      

      setReportStatusError(error.message);

      throw error;
    } finally {
      setIsReportCreating(false);
    }
  };

  useEffect(() => {
    if (!reportId) {
      return;
    }

    let isMounted = true;
    let intervalId = null;

    const loadReportStatus = async () => {
      try {
        const responseBody =
          await fetchReportById(reportId);

        const report =
          responseBody?.data ?? responseBody;

        if (!isMounted) {
          return;
        }

        setReportDetail(report);
        setReportStatusError(null);

        const currentStatus = String(
          report?.status || ""
        ).toLowerCase();

        

        if (currentStatus === "generated") {
          onGeneratedRef.current?.(report);

          setReportId(null);

          if (intervalId) {
            clearInterval(intervalId);
            intervalId = null;
          }
        }
      } catch (error) {
        

        if (isMounted) {
          setReportStatusError(error.message);
        }
      }
    };

    loadReportStatus();

    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        loadReportStatus();
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [reportId, refreshInterval]);

  return {
    reportId,
    reportDetail,
    isReportCreating,
    reportStatusError,
    createOperationReport,
  };
};