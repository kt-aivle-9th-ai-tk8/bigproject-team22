import { apiFetch } from "./apiClient";

export const fetchNotifications = async () => {
  const requestUrl = "/api/notifications";

  const response = await fetch(requestUrl, {
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
        "알림 목록을 불러오지 못했습니다."
    );
  }

  return responseBody;
};

export const readNotification = async (
  notificationId
) => {
  if (!notificationId) {
    throw new Error(
      "알림 ID가 필요합니다."
    );
  }

  const response = await apiFetch(
    `/api/notifications/${notificationId}/read`,
    {
      method: "PATCH",
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
    responseBody = null;
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "알림 읽음 처리에 실패했습니다."
    );
  }

  return responseBody;
};

export const deleteNotification = async (
  notificationId
) => {
  if (!notificationId) {
    throw new Error(
      "알림 ID가 필요합니다."
    );
  }

  const response = await apiFetch(
    `/api/notifications/${notificationId}`,
    {
      method: "DELETE",
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
    responseBody = null;
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "알림 삭제에 실패했습니다."
    );
  }

  return responseBody;
};