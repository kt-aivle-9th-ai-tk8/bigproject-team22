import { WEATHER_TYPE } from "../components/SideBar/Weather/WeatherItem";
import { FAULT_STATUS } from "../components/SideBar/Fault/FaultItem";
import { TURBINE_STATUS } from "../components/SideBar/Turbine/TurbineItem";

export const normalizeWeatherType = (weatherType) => {
  if (!weatherType) {
    return WEATHER_TYPE.CLEAR;
  }

  return WEATHER_TYPE[weatherType] || WEATHER_TYPE.CLEAR;
};

export const convertWindFarmToPlant = (windFarm) => {
  const weather = windFarm.weather || {};
  const power = windFarm.power || {};

  return {
    id: windFarm.id,
    name: windFarm.name,
    capacity: windFarm.capacity,

    coordinate: [
      windFarm.longitude,
      windFarm.latitude,
    ],

    weather: {
      weatherType: normalizeWeatherType(weather.weather_type),
      temperature: weather.temperature ?? 0,
      windSpeed: weather.wind_speed ?? 0,
    },

    power: {
      currentOutput: power.current_power ?? 0,
      currentPower: power.today_power ?? 0,
      monthPower: power.month_power ?? 0,
      yearPower: 0,
    },

    faults: [],
    turbines: [],
  };
};

export const convertTurbineToMapObject = (turbine) => {
  let status = TURBINE_STATUS.NORMAL;

  if (turbine.current_power === null) {
    status = TURBINE_STATUS.NO_DATA;
  } else if (turbine.current_power <= 0) {
    status = TURBINE_STATUS.ZERO_POWER;
  }

  return {
    id: turbine.id,
    name: turbine.code,
    code: turbine.code,
    model: turbine.model,
    status,
    abnormalDetected: false,

    coordinate: [
      turbine.longitude,
      turbine.latitude,
    ],
  };
};
export const convertWindFarmDetailToPlant = (windFarm) => {
  const weather = windFarm.weather || {};
  const power = windFarm.power || {};
  const turbines = windFarm.turbines || [];

  return {
    id: windFarm.id,
    name: windFarm.name,
    capacity: windFarm.capacity,

    weather: {
      weatherType: normalizeWeatherType(weather.weather_type),
      temperature: weather.temperature ?? 0,
      windSpeed: weather.wind_speed ?? 0,
    },

    power: {
      currentOutput: power.current_power ?? 0,
      currentPower: power.today_power ?? 0,
      monthPower: power.month_power ?? 0,
      yearPower: 0,
    },

    turbines: turbines.map(convertTurbineToMapObject),
  };
};