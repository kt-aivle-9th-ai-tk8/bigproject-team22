let isHandlingSessionExpired = false;

export const apiFetch = async (url, options = {}) => {
  const response = await fetch(url, options);

  if (
    response.status === 403 ||
    response.status === 404
  ) {
    if (!isHandlingSessionExpired) {
      isHandlingSessionExpired = true;

      localStorage.removeItem("screenMode");
      localStorage.removeItem("selectedPlant");
      localStorage.removeItem("selectedTurbine");

      alert("로그인 세션이 만료 되었습니다.");

      window.location.replace("/login");
    }

    throw new Error(
      "로그인 세션이 만료 되었습니다."
    );
  }

  return response;
};