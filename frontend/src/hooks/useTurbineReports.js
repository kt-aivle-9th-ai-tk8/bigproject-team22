import { useEffect, useState } from "react";

import { fetchReports } from "../api/reportApi";


const AVAILABLE_REPORT_TYPES = [
  "turbine_operation",
  "defect_diagnosis",
  "anomaly_event",
];

const getTurbineReportDisplay = (title) => {
  if (!title) {
    return {
      title: "보고서",
      subtitle: "",
    };
  }

  const datePattern =
    /(\d{4}-\d{2}-\d{2})(?:\s*~\s*(\d{4}-\d{2}-\d{2}))?/;

  const match = title.match(datePattern);

  if (!match) {
    return {
      title,
      subtitle: "",
    };
  }

  const startDate = match[1];
  const endDate = match[2];

  const formattedStartDate =
    startDate.replaceAll("-", ".");

  const formattedEndDate =
    endDate
      ? endDate.replaceAll("-", ".")
      : "";

  const subtitle =
    endDate && endDate !== startDate
      ? `${formattedStartDate} ~ ${formattedEndDate}`
      : formattedStartDate;

  const displayTitle = title
    .replace(datePattern, "")
    .replace(/\s+/g, " ")
    .trim();

  return {
    title: displayTitle || "보고서",
    subtitle,
  };
};

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
        
        console.log("reports: ", reports)

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

            let displayTitle = report.title;
            let subtitle = "";

            if (
              reportType ===
              "turbine_operation"
            ) {
              const display =
                getTurbineReportDisplay(
                  report.title
                );

              displayTitle =
                display.title;

              subtitle =
                display.subtitle;
            }

            return {
              ...report,

              title: displayTitle,
              subtitle,

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