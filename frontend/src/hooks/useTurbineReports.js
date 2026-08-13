import { useEffect, useState } from "react";

import { fetchReports } from "../api/reportApi";


const AVAILABLE_REPORT_TYPES = [
  "turbine_operation",
  "defect_diagnosis",
  "anomaly_event",
];


export const useTurbineReports = ({
  mode,
  windFarmId,
  turbineId,
  refreshInterval = 10000,
}) => {
  const [reportItems, setReportItems] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);


  useEffect(() => {
    if (mode !== "turbine") {
      setReportItems([]);
      return;
    }

    if (!windFarmId || !turbineId) {
      setReportItems([]);
      return;
    }


    let isMounted = true;
    let intervalId = null;


    const loadReports = async () => {
      try {
        setIsLoading(true);
        setError(null);


        const responseBody = await fetchReports({
          windFarmId,
          turbineId,
        });


        const responseData =
          responseBody?.data ?? responseBody;


        const reports = Array.isArray(responseData)
          ? responseData
          : Array.isArray(responseData?.content)
            ? responseData.content
            : [];


        const nextReportItems = reports
          .filter((report) => {
            const reportType = String(
              report?.report_type || ""
            ).toLowerCase();

            const reportStatus = String(
              report?.status || ""
            ).toLowerCase();

            return (
              AVAILABLE_REPORT_TYPES.includes(reportType) &&
              reportStatus === "generated"
            );
          })
          .sort((a, b) => {
            return (
              new Date(b.generated_at).getTime() -
              new Date(a.generated_at).getTime()
            );
          })
          .slice(0, 4)
          .map((report) => {
            const reportType = String(
              report.report_type || ""
            ).toLowerCase();

            const generatedDate =
              report.generated_at
                ? report.generated_at
                    .slice(0, 10)
                    .replaceAll("-", ".")
                : "";

            const displayTitle =
              reportType === "turbine_operation"
                ? `${generatedDate} 운영보고서`
                : report.title;

            return {
              ...report,

              title: displayTitle,

              status: reportType,

              reportStatus:
                report.status,
            };
          });


        if (!isMounted) {
          return;
        }


        setReportItems(
          nextReportItems
        );


        console.log(
          "터빈 보고서 목록:",
          nextReportItems
        );
      } catch (error) {
        console.error(
          "터빈 보고서 목록 조회 오류:",
          error
        );

        if (isMounted) {
          setError(
            error.message
          );

          setReportItems([]);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };


    /*
     * turbine 진입 즉시 1회 조회
     */
    loadReports();


    /*
     * 이후 polling
     */
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        loadReports();
      }, refreshInterval);
    }


    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(
          intervalId
        );
      }
    };
  }, [
    mode,
    windFarmId,
    turbineId,
    refreshInterval,
  ]);


  return {
    reportItems,
    isLoading,
    error,
  };
};