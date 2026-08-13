"""SCADA × AWS → ML 파생 7컬럼 백필 CSV 생성기 (ml_backfill.csv.gz).

V4 가 scada_record 에 붙인 파생 컬럼(air_density, norm_wind_speed, is_stopped, train_mask,
expected_power_pooled, expected_power_unit)의 **리포지토리 내 재생성 경로**다(V14 주석이 "없다"고
한 그 경로). 소비자: report-agent datasource, anomaly 계층 A/B 판정, 운영/단지 보고서.

기작 (serving/ml == Lightgbm_v1 핸드오프와 동일 코드·상수):
  AWS(기온·기압[현지]·습도) → 습윤공기밀도 → 허브고도 보정(DZ_M) → air_density
  norm_wind_speed = wind_speed × (ρ/1.225)^(1/3)          (IEC 61400-12-1)
  LightGBM(pooled/터빈별, 피처=norm_wind_speed 1개) → expected_power_{pooled,unit}
  is_stopped = (출력<0 AND 풍속≥컷인) / train_mask = 정상거동(학습 전용, flags.py 로직)

결측 전파: 풍속·기온·기압·습도 중 하나라도 없으면 norm=NULL → expected 둘 다 NULL(예측 불가 신호).
플래그는 풍속·출력이 모두 있을 때만 계산하고, 아니면 NULL.

터빈 코드 매핑(이슈 #96): DB ground-truth 는 장흥 '1'~'6'·화순 'U1'~'U8', 모델 manifest 는
양쪽 다 'U*' — 장흥만 'U'+코드로 변환해 모델을 찾는다.

사용:
  python3 build_ml_backfill.py --lgbm-root <Lightgbm_v1 압축해제 경로> \
      [--scada scada_hourly.csv.gz] [--aws aws_hourly.csv.gz] [-o ml_backfill.csv.gz]
필요 패키지: pandas, numpy, lightgbm  (--lgbm-root 의 density/predict 모듈을 그대로 import)
"""
import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd

# ── 단지 상수 (DB ground-truth: V10 / 모델: manifest_v1) ─────────────────────
FARM_OF_TURBINE = {**{t: "jangheung" for t in range(1, 7)},
                   **{t: "hwasun" for t in range(7, 15)}}
STATION_OF_FARM = {"jangheung": 778, "hwasun": 741}   # wind_farm.aws_station_id (V10)
# flags.FARM_SPEC 과 동일 — 정격(kW)·컷인(m/s)
FARM_SPEC = {"jangheung": {"rated_kw": 3000.0, "cut_in_ms": 3.0},
             "hwasun": {"rated_kw": 2000.0, "cut_in_ms": 3.0}}
UNDERCURVE_RATIO = 0.5
UNDERCURVE_MIN_MED = 0.05


def model_code(farm: str, db_code: str) -> str:
    """DB turbine_code → 모델 manifest 의 turbine_code. 장흥('1'~'6')만 'U' 접두가 필요하다."""
    return db_code if db_code.startswith("U") else f"U{db_code}"


def load_inputs(scada_path: Path, aws_path: Path) -> pd.DataFrame:
    scada = pd.read_csv(scada_path, header=None,
                        names=["turbine_id", "recorded_at", "power_output", "wind_speed"],
                        parse_dates=["recorded_at"])
    aws = pd.read_csv(aws_path, header=None,
                      names=["station", "recorded_at", "temperature", "pressure",
                             "humidity", "wind_direction", "precipitation"],
                      parse_dates=["recorded_at"])
    scada["farm"] = scada["turbine_id"].map(FARM_OF_TURBINE)
    if scada["farm"].isna().any():
        bad = sorted(scada.loc[scada["farm"].isna(), "turbine_id"].unique())
        raise SystemExit(f"미지의 turbine_id (FARM_OF_TURBINE 갱신 필요): {bad}")
    scada["station"] = scada["farm"].map(STATION_OF_FARM)
    return scada.merge(aws[["station", "recorded_at", "temperature", "pressure", "humidity"]],
                       on=["station", "recorded_at"], how="left")


def add_density_and_norm(df: pd.DataFrame, density_at_height, dz_m: dict) -> pd.DataFrame:
    """벡터화 밀도·보정풍속. 기상 3종 중 하나라도 NaN 이면 둘 다 NaN(전파)."""
    out = df.copy()
    out["air_density"] = np.nan
    for farm, dz in dz_m.items():
        m = (out["farm"] == farm) & out[["temperature", "pressure", "humidity"]].notna().all(axis=1)
        out.loc[m, "air_density"] = density_at_height(
            out.loc[m, "temperature"].to_numpy(),
            out.loc[m, "pressure"].to_numpy(),
            out.loc[m, "humidity"].to_numpy(), dz)
    out["norm_wind_speed"] = out["wind_speed"] * (out["air_density"] / 1.225) ** (1.0 / 3.0)
    return out


def add_flags(df: pd.DataFrame) -> pd.DataFrame:
    """flags.py 의 is_stopped·train_mask 를 turbine_id 기준으로 재현(풍속·출력 결측 행은 NULL)."""
    out = df.copy()
    ws, pw = out["wind_speed"], out["power_output"]
    have = ws.notna() & pw.notna()
    cut_in = out["farm"].map(lambda f: FARM_SPEC[f]["cut_in_ms"])
    rated = out["farm"].map(lambda f: FARM_SPEC[f]["rated_kw"])

    below_cutin = ws < cut_in
    highwind_neg = (pw < 0) & (ws >= cut_in)            # = is_stopped
    bin_ws = (ws * 2).round() / 2
    bin_med = out.assign(_bin=bin_ws).groupby(["turbine_id", "_bin"])["power_output"].transform("median")
    undercurve = (pw >= 0) & (ws >= cut_in) & (bin_med >= UNDERCURVE_MIN_MED * rated) \
        & (pw < UNDERCURVE_RATIO * bin_med)

    out["is_stopped"] = np.where(have, highwind_neg.astype(int), np.nan)
    out["train_mask"] = np.where(have, (~(below_cutin | highwind_neg | undercurve)).astype(int), np.nan)
    return out


def add_predictions(df: pd.DataFrame, farm_models: dict, code_of_turbine: dict) -> pd.DataFrame:
    """터빈별로 모아 배치 예측(행 단위 호출은 91만 행에서 비현실적)."""
    out = df.copy()
    out["expected_power_pooled"] = np.nan
    out["expected_power_unit"] = np.nan
    for tid, sub in out.groupby("turbine_id"):
        farm = FARM_OF_TURBINE[int(tid)]
        models = farm_models[farm]
        m = sub["norm_wind_speed"].notna()
        if not m.any():
            continue
        x = sub.loc[m, "norm_wind_speed"].to_numpy().reshape(-1, 1)
        idx = sub.index[m]
        out.loc[idx, "expected_power_pooled"] = models.pooled.predict(x)
        unit = models.per_unit.get(model_code(farm, code_of_turbine[int(tid)]))
        if unit is None:
            raise SystemExit(f"turbine_id={tid} 의 터빈별 모델이 없다 "
                             f"(코드 매핑 확인: {code_of_turbine[int(tid)]})")
        out.loc[idx, "expected_power_unit"] = unit.predict(x)
    return out


def main() -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--lgbm-root", required=True, help="Lightgbm_v1 압축해제 경로(density/predict/models)")
    ap.add_argument("--scada", default=str(here / "scada_hourly.csv.gz"))
    ap.add_argument("--aws", default=str(here / "aws_hourly.csv.gz"))
    ap.add_argument("-o", "--out", default=str(here / "ml_backfill.csv.gz"))
    args = ap.parse_args()

    lgbm_root = Path(args.lgbm_root).resolve()
    sys.path.insert(0, str(lgbm_root))
    from density import density_at_height          # noqa: E402 — 핸드오프 모듈 그대로
    from predict import FarmModels, DZ_M           # noqa: E402

    # DB ground-truth 터빈 코드(V10). 장흥 '1'~'6', 화순 'U1'~'U8'.
    code_of_turbine = {**{t: str(t) for t in range(1, 7)},
                       **{t: f"U{t - 6}" for t in range(7, 15)}}

    df = load_inputs(Path(args.scada), Path(args.aws))
    print(f"조인: {len(df):,}행, AWS 매칭 { df[['temperature','pressure','humidity']].notna().all(axis=1).mean():.1%}")

    df = add_density_and_norm(df, density_at_height, DZ_M)
    df = add_flags(df)
    farm_models = {farm: FarmModels(farm, model_root=lgbm_root / "models")
                   for farm in sorted(set(FARM_OF_TURBINE.values()))}
    df = add_predictions(df, farm_models, code_of_turbine)

    # 출력 — 반올림으로 용량 절감(밀도·풍속 4자리, 출력 2자리면 소비자 정밀도 요구를 넉넉히 넘는다)
    cols = ["turbine_id", "recorded_at", "air_density", "norm_wind_speed",
            "is_stopped", "train_mask", "expected_power_pooled", "expected_power_unit"]
    out = df[cols].copy()
    out["recorded_at"] = out["recorded_at"].dt.strftime("%Y-%m-%d %H:%M:%S")
    for c, nd in (("air_density", 4), ("norm_wind_speed", 4),
                  ("expected_power_pooled", 2), ("expected_power_unit", 2)):
        out[c] = out[c].round(nd)
    for c in ("is_stopped", "train_mask"):
        out[c] = out[c].astype("Int64")  # NULL 가능 정수 → CSV 에서 빈 칸
    out.to_csv(args.out, index=False, header=False, compression="gzip")

    # 사후 통계 — 적재 전에 눈으로 확인할 것들
    valid = df["expected_power_unit"].notna()
    print(f"→ {args.out} : {len(out):,}행")
    print(f"  norm 보유 {df['norm_wind_speed'].notna().mean():.1%} · expected 보유 {valid.mean():.1%}"
          f" · is_stopped=1 {int((df['is_stopped'] == 1).sum()):,}행")
    both = df[valid & df["power_output"].notna()]
    if len(both):
        corr = both["expected_power_unit"].corr(both["power_output"])
        print(f"  sanity: corr(expected_unit, actual) = {corr:.4f} (유효 {len(both):,}행)")
    for tid, sub in df.groupby("turbine_id"):
        print(f"  t{int(tid):>2}: expected 보유 {sub['expected_power_unit'].notna().mean():>6.1%}"
              f"  기간 {sub['recorded_at'].min():%Y-%m-%d}~{sub['recorded_at'].max():%Y-%m-%d}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
