export const loginApi = async (credentials) => {
  console.log("[loginApi] 요청 payload:", credentials);

  const response = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(credentials),
  });

  console.log("[loginApi] 응답 상태:", response.status);
  console.log("[loginApi] 응답 OK:", response.ok);
  console.log(
    "[loginApi] Content-Type:",
    response.headers.get("content-type")
  );

  const responseText = await response.text();

  console.log("[loginApi] 응답 원문:", responseText);

  let responseBody = null;

  try {
    responseBody = responseText ? JSON.parse(responseText) : null;
  } catch {
    throw new Error("로그인 API 응답이 JSON이 아닙니다.");
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "로그인 오류가 발생했습니다."
    );
  }

  return responseBody;
};

export const signupApi = async (userData) => {
  const response = await fetch("/api/users", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(userData),
  });

  const responseBody = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(
      responseBody?.message || "회원가입 오류가 발생했습니다."
    );
  }

  return responseBody;
};

export const logout = async () => {
  const response = await fetch("/api/auth/logout", {
    method: "POST",
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
      responseBody = responseText;
    }
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
      "로그아웃에 실패했습니다."
    );
  }

  return responseBody;
};