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
    

    

    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    

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
        "터빈 발전량 데이터를 불러오지 못했습니다."
    );
  }

  return responseBody;
};