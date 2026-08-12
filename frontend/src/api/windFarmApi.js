import { apiFetch } from "./apiClient";

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

  const response = await apiFetch(requestUrl, {
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

export const fetchWindFarmById = async (windFarmId) => {
  if (windFarmId === undefined || windFarmId === null) {
    throw new Error("발전소 ID가 필요합니다.");
  }

  const requestUrl = `/api/wind-farms/${windFarmId}`;

  console.log("[fetchWindFarmById] 요청 URL:", requestUrl);

  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  console.log(
    "[fetchWindFarmById] 응답 상태:",
    response.status
  );

  console.log(
    "[fetchWindFarmById] 응답 OK:",
    response.ok
  );

  console.log(
    "[fetchWindFarmById] Content-Type:",
    response.headers.get("content-type")
  );

  const responseText = await response.text();

  console.log(
    "[fetchWindFarmById] 응답 원문:",
    responseText
  );

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;

    console.log(
      "[fetchWindFarmById] JSON 파싱 결과:",
      responseBody
    );
  } catch (error) {
    console.error(
      "[fetchWindFarmById] JSON 파싱 실패:",
      error
    );

    console.error(
      "[fetchWindFarmById] 응답이 HTML인지 확인:",
      responseText.slice(0, 300)
    );

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    console.error(
      "[fetchWindFarmById] API 실패 body:",
      responseBody
    );

    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "발전소 상세 정보를 불러오지 못했습니다."
    );
  }

  return responseBody;
};

export const fetchWindFarmPower = async ({
  windFarmId,
  startTime,
  endTime,
  term,
}) => {
  if (!windFarmId) {
    throw new Error("발전소 ID가 필요합니다.");
  }

  const params = new URLSearchParams();

  if (startTime) {
    params.append("start_time", startTime);
  }

  if (endTime) {
    params.append("end_time", endTime);
  }

  if (term) {
    params.append("term", term);
  }

  const requestUrl =
    `/api/wind-farms/${windFarmId}/power?${params.toString()}`;

  console.log("[fetchWindFarmPower] 요청 URL:", requestUrl);

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
    console.error(
      "[fetchWindFarmPower] JSON 파싱 실패:",
      error
    );

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "발전량 데이터를 불러오지 못했습니다."
    );
  }

  return responseBody;
};

export const fetchTurbinePower = async ({
  turbineId,
  startTime,
  endTime,
  term,
}) => {
  if (!turbineId) {
    throw new Error("터빈 ID가 필요합니다.");
  }

  const params = new URLSearchParams();

  if (startTime) {
    params.append("start_time", startTime);
  }

  if (endTime) {
    params.append("end_time", endTime);
  }

  if (term) {
    params.append("term", term);
  }

  const requestUrl =
    `/api/turbines/${turbineId}/power?${params.toString()}`;

  console.log("[fetchTurbinePower] 요청 URL:", requestUrl);

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
    console.error(
      "[fetchTurbinePower] JSON 파싱 실패:",
      error
    );

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "터빈 발전량 데이터를 불러오지 못했습니다."
    );
  }

  return responseBody;
};