import { WEATHER_TYPE } from "../components/SideBar/Weather/WeatherItem";
import { FAULT_STATUS } from "../components/SideBar/Fault/FaultItem";
import { TURBINE_STATUS } from "../components/SideBar/Turbine/TurbineItem";

export const normalizeWeatherType = (weatherType) => {
  if (!weatherType) {
    return WEATHER_TYPE.CLEAR;
  }

  return WEATHER_TYPE[weatherType] || WEATHER_TYPE.CLEAR;
};

const getDummyTurbinesByWindFarmId = (windFarmId) => {
  if (windFarmId !== 1) {
    return [];
  }

  return [
    {
      id: 1,
      name: "터빈 A",
      status: TURBINE_STATUS.NORMAL,
      abnormalDetected: false,
      coordinate: [126.9064, 34.6815],
    },
    {
      id: 2,
      name: "터빈 B",
      status: TURBINE_STATUS.ZERO_POWER,
      abnormalDetected: true,
      coordinate: [126.9077, 34.6816],
    },
    {
      id: 3,
      name: "터빈 C",
      status: TURBINE_STATUS.NO_DATA,
      abnormalDetected: false,
      coordinate: [126.9066, 34.6804],
    },
    {
      id: 4,
      name: "터빈 D",
      status: TURBINE_STATUS.NORMAL,
      abnormalDetected: false,
      coordinate: [126.9078, 34.6805],
    },
  ];
};

const getDummyFaultsByWindFarmId = (windFarmId) => {
  if (windFarmId === 1) {
    return [
      {
        id: 1,
        date: "07.08",
        time: "12:00",
        status: FAULT_STATUS.ALERT,
      },
    ];
  }

  if (windFarmId === 3) {
    return [
      {
        id: 2,
        date: "07.08",
        time: "11:00",
        status: FAULT_STATUS.WARNING,
      },
    ];
  }

  return [];
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

    faults: getDummyFaultsByWindFarmId(windFarm.id),
    turbines: getDummyTurbinesByWindFarmId(windFarm.id),
  };
};