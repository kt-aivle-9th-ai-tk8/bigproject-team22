"""밀도보정풍속 계산 + LightGBM 기대발전량 예측 — BE 핸드오프용 자립 모듈.

원본 풍속을 그대로 모델에 넣으면 안 된다. 모델 입력은 **밀도보정풍속**(IEC 61400-12-1)이라
AWS 기상(기온·기압·습도)으로 공기밀도를 구해 보정한 값이어야 한다.

흐름:
    풍속 + 기상(기온·기압·습도) + dz_m
      → 공기밀도(density.density_at_height)
      → 밀도보정풍속 = 풍속 × (밀도/1.225)^(1/3)
      → model.predict([[밀도보정풍속]]) → 기대발전량(kW)

의존성: lightgbm, numpy  (density.py는 numpy만 사용)
"""
import json
from pathlib import Path

import lightgbm as lgb
import numpy as np

try:
    from .density import density_at_height
except ImportError:
    from density import density_at_height

HERE = Path(__file__).resolve().parent
RHO_STD = 1.225   # IEC 61400-12-1 표준 대기 밀도 (kg/m³)

# 부지표고 + 허브높이 − 기상지점 표고 [m]. 밀도를 허브고도로 보정하는 데 쓴다.
DZ_M = {"jangheung": 352.0, "hwasun": 627.0}


class FarmModels:
    """한 단지의 LightGBM 모델 묶음. 기동 시 1회 로드해 재사용한다.

    model_root: 모델 루트 디렉터리(그 아래 {farm}/manifest_{version}.json 구조).
                미지정 시 이 파일 옆의 models/ 를 사용한다.
                SageMaker 등 코드·모델 분리 배포 시 model_dir를 넘기면 된다.
    """

    def __init__(self, farm: str, version: str = "v1", model_root=None):
        self.farm = farm
        root = Path(model_root) if model_root is not None else HERE / "models"
        mdir = root / farm
        manifest = json.loads((mdir / f"manifest_{version}.json").read_text(encoding="utf-8"))
        self.pooled = None
        self.per_unit = {}   # turbine_code -> Booster
        for e in manifest["models"]:
            booster = lgb.Booster(model_file=str(mdir / e["file"]))
            if e["kind"] == "pooled":
                self.pooled = booster
            else:
                self.per_unit[e["turbine_code"]] = booster


def norm_wind_speed(wind_speed, temperature, pressure, humidity, farm: str) -> float:
    """밀도보정풍속. 기상 결측이면 NaN(예측 불가 신호)."""
    if any(v is None or (isinstance(v, float) and np.isnan(v))
           for v in (wind_speed, temperature, pressure, humidity)):
        return float("nan")
    rho = float(density_at_height(temperature, pressure, humidity, DZ_M[farm]))
    return float(wind_speed) * (rho / RHO_STD) ** (1.0 / 3.0)


def expected_power(models: FarmModels, turbine_code: str,
                   wind_speed, temperature, pressure, humidity) -> dict:
    """기대발전량(kW) 2종 반환.

    - pooled : 단지 통합 모델 (계층 B/만성 판정용)
    - unit   : 터빈별 모델   (계층 A/급성 판정·손실 추정용)
    기상·풍속 결측이면 밀도보정풍속이 NaN이라 둘 다 None을 반환한다(NULL 전파).
    """
    nws = norm_wind_speed(wind_speed, temperature, pressure, humidity, models.farm)
    if np.isnan(nws):
        return {"pooled": None, "unit": None, "norm_wind_speed": None}
    x = np.array([[nws]])
    unit_model = models.per_unit.get(turbine_code)
    return {
        "pooled": float(models.pooled.predict(x)[0]),
        "unit": float(unit_model.predict(x)[0]) if unit_model is not None else None,
        "norm_wind_speed": round(nws, 4),
    }


if __name__ == "__main__":
    # 사용 예시 — 화순 U1, 겨울 한 시점
    models = FarmModels("hwasun")
    out = expected_power(models, "U1",
                         wind_speed=3.768, temperature=-5.0, pressure=1020.9, humidity=75.0)
    print("입력: 풍속 3.768 m/s, 기온 -5.0°C, 기압 1020.9 hPa, 습도 75%")
    print(f"밀도보정풍속: {out['norm_wind_speed']} m/s")
    print(f"기대발전량  pooled: {out['pooled']:.2f} kW  /  unit(U1): {out['unit']:.2f} kW")
