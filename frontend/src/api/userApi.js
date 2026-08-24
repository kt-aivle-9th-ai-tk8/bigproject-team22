import { apiFetch } from "./apiClient";

export const fetchMyPage = async () => {
  const response = await apiFetch(
    "/api/users/mypage",
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
        "사용자 정보 조회에 실패했습니다."
    );
  }

  return responseBody;
};