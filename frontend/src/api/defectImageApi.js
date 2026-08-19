import { apiFetch } from "./apiClient";


export const fetchDefectImages = async (
  bladeId
) => {
  if (!bladeId) {
    throw new Error(
      "블레이드 ID가 필요합니다."
    );
  }

  const response = await apiFetch(
    `/api/blades/${bladeId}/defect-images`,
    {
      method: "GET",
      credentials: "include",
      headers: {
        Accept: "application/json",
      },
    }
  );

  const responseText =
    await response.text();

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;
  } catch {
    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "결함 이미지 조회에 실패했습니다."
    );
  }

  return responseBody;
};