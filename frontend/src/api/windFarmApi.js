export const fetchWindFarms = async ({
  location,
  power,
  weather,
} = {}) => {
  const params = new URLSearchParams();

  if (location !== undefined && location !== null) {
    params.append("location", String(location));
  }

  if (power !== undefined && power !== null) {
    params.append("power", String(power));
  }

  if (weather !== undefined && weather !== null) {
    params.append("weather", String(weather));
  }

  const queryString = params.toString();

  const requestUrl = `/api/wind-farms${
    queryString ? `?${queryString}` : ""
  }`;

  console.log("[fetchWindFarms] 요청 URL:", requestUrl);

  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  console.log("[fetchWindFarms] 응답 상태:", response.status);
  console.log("[fetchWindFarms] 응답 OK:", response.ok);
  console.log(
    "[fetchWindFarms] Content-Type:",
    response.headers.get("content-type")
  );

  const responseText = await response.text();

  console.log("[fetchWindFarms] 응답 원문:", responseText);

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;

    console.log(
      "[fetchWindFarms] JSON 파싱 결과:",
      responseBody
    );
  } catch (error) {
    console.error(
      "[fetchWindFarms] JSON 파싱 실패:",
      error
    );

    console.error(
      "[fetchWindFarms] 응답이 HTML인지 확인:",
      responseText.slice(0, 300)
    );

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    console.error(
      "[fetchWindFarms] API 실패 body:",
      responseBody
    );

    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "발전소 목록을 불러오지 못했습니다."
    );
  }

  return responseBody;
};