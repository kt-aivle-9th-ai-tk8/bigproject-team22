let isHandlingSessionExpired = false;

export const apiFetch = async (url, options = {}) => {
  const response = await fetch(url, options);

  let responseBody = null;

  try {
    responseBody = await response.clone().json();
  } catch {
    responseBody = null;
  }

  const isSessionExpired =
    response.status === 404 ||
    responseBody?.code === "A001";

  if (isSessionExpired) {
    if (!isHandlingSessionExpired) {
      isHandlingSessionExpired = true;

      localStorage.removeItem("screenMode");
      localStorage.removeItem("selectedPlant");
      localStorage.removeItem("selectedTurbine");

      alert("로그인 세션이 만료 되었습니다.");

      window.location.replace("/login");
    }

    throw new Error(
      responseBody?.message ||
        "로그인 세션이 만료 되었습니다."
    );
  }

  return response;
};