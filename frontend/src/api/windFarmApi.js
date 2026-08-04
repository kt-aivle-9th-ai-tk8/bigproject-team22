export const fetchWindFarms = async ({
  topN,
  location = 1,
  power = 1,
  weather = 1,
} = {}) => {
  const params = new URLSearchParams();

  if (topN !== undefined && topN !== null) {
    params.append("top-n", String(topN));
  }

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

  const response = await fetch(
    `/api/wind-farms${queryString ? `?${queryString}` : ""}`
  );

  if (!response.ok) {
    throw new Error("발전소 목록을 불러오지 못했습니다.");
  }

  return response.json();
};