import { apiFetch } from "./apiClient";

export const fetchNotifications = async () => {
  const requestUrl = "/api/notifications";

  console.log("[fetchNotifications] 요청 URL:", requestUrl);

  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  console.log(
    "[fetchNotifications] 응답 상태:",
    response.status
  );

  console.log(
    "[fetchNotifications] 응답 OK:",
    response.ok
  );

  console.log(
    "[fetchNotifications] Content-Type:",
    response.headers.get("content-type")
  );

  const responseText = await response.text();

  console.log(
    "[fetchNotifications] 응답 원문:",
    responseText
  );

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;

    console.log(
      "[fetchNotifications] JSON 파싱 결과:",
      responseBody
    );
  } catch (error) {
    console.error(
      "[fetchNotifications] JSON 파싱 실패:",
      error
    );

    console.error(
      "[fetchNotifications] 응답이 HTML인지 확인:",
      responseText.slice(0, 300)
    );

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    console.error(
      "[fetchNotifications] API 실패 body:",
      responseBody
    );

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