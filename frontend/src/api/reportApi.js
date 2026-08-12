export const createReport = async ({
  windFarmId,
  turbineId,
  periodStart,
  periodEnd,
  reportType,
}) => {
  const requestBody = {
    wind_farm_id: windFarmId,
    period_start: periodStart,
    period_end: periodEnd,
    report_type: reportType,
  };

  if (turbineId !== undefined && turbineId !== null) {
    requestBody.turbine_id = turbineId;
  }

  const response = await fetch("/api/reports", {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });

  const responseText = await response.text();

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;
  } catch (error) {
    console.error("[createReport] JSON 파싱 실패:", error);

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "보고서 생성에 실패했습니다."
    );
  }

  return responseBody;
};

export const fetchReportById = async (reportId) => {
  if (reportId === undefined || reportId === null) {
    throw new Error("보고서 ID가 필요합니다.");
  }

  const response = await fetch(`/api/reports/${reportId}`, {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  const responseText = await response.text();

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;
  } catch (error) {
    console.error("[fetchReportById] JSON 파싱 실패:", error);

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "보고서 정보를 불러오지 못했습니다."
    );
  }

  return responseBody;
};