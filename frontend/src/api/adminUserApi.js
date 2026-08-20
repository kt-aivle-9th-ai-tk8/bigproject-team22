import { apiFetch } from "./apiClient";

export const fetchAdminUsers = async ({
  role,
} = {}) => {
  const params = new URLSearchParams();

  if (role) {
    params.append("role", role);
  }

  const queryString = params.toString();

  const url = queryString
    ? `/api/admin/users?${queryString}`
    : "/api/admin/users";

  const response = await apiFetch(url, {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  const responseText = await response.text();

  let responseBody = null;

  if (responseText) {
    try {
      responseBody = JSON.parse(responseText);
    } catch {
      throw new Error(
        "서버에서 JSON이 아닌 응답을 받았습니다."
      );
    }
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "사용자 목록 조회에 실패했습니다."
    );
  }

  return responseBody;
};

export const forceLogoutAdminUser = async (userId) => {
  if (!userId) {
    throw new Error("사용자 ID가 필요합니다.");
  }

  const response = await apiFetch(
    `/api/admin/users/${userId}/session`,
    {
      method: "DELETE",
      credentials: "include",
      headers: {
        Accept: "application/json",
      },
    }
  );

  const responseText = await response.text();

  let responseBody = null;

  if (responseText) {
    try {
      responseBody = JSON.parse(responseText);
    } catch {
      responseBody = responseText;
    }
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "강제 로그아웃 처리에 실패했습니다."
    );
  }

  return responseBody;
};

export const updateAdminUser = async ({
  userId,
  role,
  windFarmIds,
}) => {
  if (userId === undefined || userId === null) {
    throw new Error("사용자 ID가 필요합니다.");
  }

  if (!role) {
    throw new Error("사용자 권한이 필요합니다.");
  }

  if (!Array.isArray(windFarmIds)) {
    throw new Error(
      "담당 발전소 ID 목록이 필요합니다."
    );
  }

  const requestBody = {
    role,
    wind_farm_ids: windFarmIds,
  };

  console.log(
    `PATCH /api/admin/users/${userId} 요청:`,
    requestBody
  );

  const response = await apiFetch(
    `/api/admin/users/${userId}`,
    {
      method: "PATCH",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
    }
  );

  const responseText = await response.text();

  let responseBody = null;

  if (responseText) {
    try {
      responseBody = JSON.parse(responseText);
    } catch {
      responseBody = responseText;
    }
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "사용자 정보 변경에 실패했습니다."
    );
  }

  return responseBody;
};

export const deleteAdminUser = async (
  userId
) => {
  if (!userId) {
    throw new Error(
      "사용자 ID가 필요합니다."
    );
  }

  const response = await apiFetch(
    `/api/admin/users/${userId}`,
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
        "사용자 삭제에 실패했습니다."
    );
  }

  return responseBody;
};