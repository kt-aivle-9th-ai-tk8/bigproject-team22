"""
습윤공기 밀도 + 기압 고도보정 (이상기체 근사)

명명 주의: CIPM-2007 완전식(압축인자 Z 포함)이 아니라 그 **이상기체 근사**다
(오차 <0.1%, 검증보고 m2). 리포트에서 'CIPM 방식'이라 부르지 않는다.

물리식:
- 포화수증기압 (Magnus, WMO 계수): es(T) = 6.112·exp(17.62·T/(243.12+T)) [hPa], T[°C]
- 수증기압: e = RH/100 · es
- 습윤공기 밀도: ρ = (P−e)·100/(Rd·T_K) + e·100/(Rv·T_K)
  Rd=287.05, Rv=461.495 J/(kg·K)
- 기압 고도보정 (측고식, 가상온도 사용):
  P(z+Δz) = P(z)·exp(−g·Δz/(Rd·Tv)),  Tv = T_K·(1 + 0.378·e/P)
- 기온 고도보정 (표준 기온감률): T(z+Δz) = T(z) − LAPSE·Δz
"""

import numpy as np

G = 9.80665          # m/s²
RD = 287.05          # J/(kg·K)
RV = 461.495         # J/(kg·K)
LAPSE = 0.0065       # K/m (표준 대기 기온감률)


def sat_vapor_pressure_hpa(t_c):
    """Magnus (WMO): 포화수증기압 [hPa]."""
    t_c = np.asarray(t_c, dtype=float)
    return 6.112 * np.exp(17.62 * t_c / (243.12 + t_c))


def vapor_pressure_hpa(t_c, rh_pct):
    return np.asarray(rh_pct, dtype=float) / 100.0 * sat_vapor_pressure_hpa(t_c)


def air_density(t_c, p_hpa, rh_pct):
    """습윤공기 밀도 [kg/m³]. 입력: 기온[°C], 기압[hPa], 상대습도[%]."""
    t_k = np.asarray(t_c, dtype=float) + 273.15
    p_pa = np.asarray(p_hpa, dtype=float) * 100.0
    e_pa = vapor_pressure_hpa(t_c, rh_pct) * 100.0
    return (p_pa - e_pa) / (RD * t_k) + e_pa / (RV * t_k)


def correct_pressure(p_hpa, t_c, rh_pct, dz_m):
    """측고식으로 기압을 Δz만큼 위(+)/아래(−)로 보정 [hPa]. 층 평균 가상온도 근사."""
    t_k = np.asarray(t_c, dtype=float) + 273.15
    p = np.asarray(p_hpa, dtype=float)
    e = vapor_pressure_hpa(t_c, rh_pct)
    tv = t_k * (1.0 + 0.378 * e / p)
    tv_layer = tv - 0.5 * LAPSE * np.asarray(dz_m, dtype=float)  # 층 중간 온도
    return p * np.exp(-G * np.asarray(dz_m, dtype=float) / (RD * tv_layer))


def density_at_height(t_c, p_hpa, rh_pct, dz_m, lapse_temp=True):
    """관측고도 대비 Δz 위 지점의 습윤공기 밀도 [kg/m³].

    기압은 측고식 보정, 기온은 표준감률 보정(lapse_temp=False면 미보정 민감도용),
    상대습도는 보존 가정(습도 연직 분포 미상 — 한계로 명시).
    """
    p2 = correct_pressure(p_hpa, t_c, rh_pct, dz_m)
    t2 = np.asarray(t_c, dtype=float) - (LAPSE * np.asarray(dz_m) if lapse_temp else 0.0)
    return air_density(t2, p2, rh_pct)
