import { apiFetch } from "./apiClient";

export const fetchTurbineById = async (turbineId) => {
  if (turbineId === undefined || turbineId === null) {
    throw new Error("터빈 ID가 필요합니다.");
  }

  const requestUrl = `/api/turbines/${turbineId}`;

  const response = await apiFetch(requestUrl, {
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
    

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "터빈 상세 정보를 불러오지 못했습니다."
    );
  }

  return responseBody;
};