"""defect 데이터 조회 — LLM 절대 사용 안 함.

반환 수치는 전부 원본 집계 그대로(임의 가공 금지). 이 값들이 critic의 검증 기준이 된다.
데이터: inspection.csv(점검 1건 = 드론 출동 1회) + defect.csv(CNN 검출 결함 1건 = 1행).
ERD상 두 테이블은 코드값을 직접 갖지 않고 FK만 갖는다 → _load()에서 조인해 붙인다.
  inspection.turbine_id → turbine.turbine_code / wind_farm.wind_farm_name  (점검 대상 터빈)
  defect.blade_id       → blade.blade_tag / blade.turbine_id → turbine.turbine_code
점검 1건은 터빈 1대를 대상으로 하며(inspection.turbine_id), 그 대상은 결함 0건이어도 알 수 있다.

배포(RDS) 전환: common.config.DATA_SOURCE == "rds" 이면 _load()를 SQLAlchemy 쿼리로
교체(위 조인을 SQL JOIN으로 옮기면 되고, 반환 컬럼·시그니처는 동일 유지).
fetch()·graph·agent·critic 은 불변.

(공유 계약) 반환 dict의 "event" 키는 공유 service.py 가 found 판정에 쓰는 자리다.
defect 에서는 그 자리에 inspection 1건이 들어간다.
"""
import os

import pandas as pd

from app.core.config import DATA_DIR

# 검출 신뢰도 하한. None이면 필터 없음(현재 데이터 최저 0.684).
# 임계 정책이 정해지면 이 값만 바꾸면 모든 집계에 일괄 반영된다.
MIN_CONFIDENCE = None

_cache = {}


def _read(name: str, **kw):
    return pd.read_csv(os.path.join(DATA_DIR, name), encoding="utf-8-sig", **kw)


def _load():
    """CSV 지연 로드(1회 캐시) + ERD 조인. anomaly만 실행할 때 defect CSV를 읽지 않도록 lazy.

    조인 결과로 inspection 에는 turbine_code·wind_farm_id·wind_farm_name 이,
    defect 에는 turbine_id·turbine_code·blade_tag 가 붙는다. 아래 집계 함수들은
    이 컬럼들이 처음부터 있었던 것처럼 그대로 쓴다(조인 위치는 여기 한 곳뿐).
    """
    if not _cache:
        blade = _read("blade.csv")[["blade_id", "blade_tag", "turbine_id"]]
        # 터빈 → 코드 + 소속 발전소. inspection·defect 양쪽이 같은 참조표를 쓴다.
        turbine = _read("turbine.csv")[["turbine_id", "turbine_code", "wind_farm_id"]].merge(
            _read("wind_farm.csv")[["wind_farm_id", "wind_farm_name"]],
            on="wind_farm_id",
            how="left",
        )

        _cache["inspection"] = _read(
            "inspection.csv",
            parse_dates=["inspection_start", "inspection_end", "created_at"],
        ).merge(turbine, on="turbine_id", how="left")

        # how="left" — FK가 깨져도 행을 잃지 않는다. 코드가 비면 집계에서 걸러진다(fetch 참고).
        _cache["defect"] = (
            _read("defect.csv", parse_dates=["created_at"])
            .merge(blade, on="blade_id", how="left")
            .merge(turbine[["turbine_id", "turbine_code"]], on="turbine_id", how="left")
        )
    return _cache["inspection"], _cache["defect"]


def _num(v):
    """NaN/빈값 → None, Timestamp → 문자열, 그 외 파이썬 스칼라로."""
    if v is None:
        return None
    if not isinstance(v, str) and pd.isna(v):
        return None
    if isinstance(v, pd.Timestamp):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def _counts(series) -> dict:
    """value_counts → 건수 내림차순 dict(파이썬 기본형)."""
    return {str(k): int(v) for k, v in series.value_counts().items()}


def get_inspection(inspection_id: int) -> dict:
    """inspection 1건 → dict. 없으면 {'found': False}.

    turbine_code·wind_farm_* 는 _load()의 조인이 붙여준 값이다(원본 테이블에는 FK만 있음).
    """
    insp, _ = _load()
    row = insp[insp["inspection_id"] == inspection_id]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    return {
        "found": True,
        "inspection_id": int(r["inspection_id"]),
        "turbine_id": _num(r["turbine_id"]),
        "turbine_code": _num(r["turbine_code"]),
        "wind_farm_id": _num(r["wind_farm_id"]),
        "wind_farm_name": _num(r["wind_farm_name"]),
        "report_id": _num(r["report_id"]),
        "inspection_start": _num(r["inspection_start"]),
        "inspection_end": _num(r["inspection_end"]),
        "user_id": _num(r["user_id"]),
        "status": r["status"],
    }


def get_defects(inspection_id: int):
    """해당 inspection 의 결함 행 전체(DataFrame). MIN_CONFIDENCE 적용."""
    _, defect = _load()
    df = defect[defect["inspection_id"] == inspection_id]
    if MIN_CONFIDENCE is not None:
        df = df[df["confidence"] >= MIN_CONFIDENCE]
    return df


def _image_seq(df) -> dict:
    """(blade_tag, part_side)별로 이미지에 1부터 순번 → {image_path: seq}.

    보고서의 'A블레이드 앞전 2번째 사진'의 그 순번. 파일명이 아니라 컬럼 기준으로 센다
    (파일명의 '..._A_LeadingEdge_002' 표기는 실제 blade_tag/part_side 와 일치하지 않음).
    """
    seq = {}
    key = df[["blade_tag", "part_side", "image_path"]].drop_duplicates()
    for _, grp in key.groupby(["blade_tag", "part_side"], sort=True):
        for i, path in enumerate(sorted(grp["image_path"]), start=1):
            seq[path] = i
    return seq


def _image_rows(df) -> list:
    """이미지 1장 = dict 1개. 심각도 높은 순 → 결함 많은 순 → 경로 순 정렬.

    severity_counts 는 '심각도별 건수', type_counts 는 '유형별 건수'.
    types_by_severity 는 '유형(심각도) N건' 문장을 만들기 위한 (유형, 심각도) 교차 집계.
    """
    seq = _image_seq(df)
    rows = []
    for path, g in df.groupby("image_path"):
        r0 = g.iloc[0]
        cross = g.groupby(["defect_type", "severity"]).size()
        rows.append(
            {
                "image_path": path,
                "blade_tag": r0["blade_tag"],
                "part_side": r0["part_side"],
                "seq": seq.get(path),
                "n_defects": int(len(g)),
                "max_severity": int(g["severity"].max()),
                "severity_counts": {
                    str(s): int((g["severity"] == s).sum())
                    for s in sorted(g["severity"].unique(), reverse=True)
                },
                "type_counts": _counts(g["defect_type"]),
                "types_by_severity": [
                    {"defect_type": t, "severity": int(s), "n": int(n)}
                    for (t, s), n in sorted(
                        cross.items(), key=lambda kv: (-kv[0][1], -kv[1], kv[0][0])
                    )
                ],
                "conf_min": round(float(g["confidence"].min()), 3),
                "conf_max": round(float(g["confidence"].max()), 3),
            }
        )
    rows.sort(key=lambda x: (-x["max_severity"], -x["n_defects"], x["image_path"]))
    return rows


def _aggregate(df, turbine_code: str) -> dict:
    """터빈 1대의 결함 집계."""
    base = {
        "turbine_code": turbine_code,
        "n_defects": int(len(df)),
        "n_images": int(df["image_path"].nunique()) if len(df) else 0,
    }
    if df.empty:
        return {
            **base,
            "max_severity": None,
            "severity_counts": {},
            "type_counts": {},
            "blade_counts": {},
            "side_counts": {},
            "confidence": {},
            "images": [],
        }
    return {
        **base,
        "max_severity": int(df["severity"].max()),
        # 심각도는 1~4 고정. 심각한 것이 먼저 오도록 내림차순으로 담는다.
        "severity_counts": {
            str(s): int((df["severity"] == s).sum())
            for s in sorted(df["severity"].unique(), reverse=True)
        },
        "type_counts": _counts(df["defect_type"]),
        "blade_counts": _counts(df["blade_tag"]),
        "side_counts": _counts(df["part_side"]),
        "confidence": {
            "min": round(float(df["confidence"].min()), 3),
            "mean": round(float(df["confidence"].mean()), 3),
            "max": round(float(df["confidence"].max()), 3),
        },
        "images": _image_rows(df),
    }


def _summarize(turbines: list, df) -> dict:
    """개요용 전체 요약 — 심각도 분포 / 최다 결함 유형 / 결함 최다 터빈."""
    with_defect = [t for t in turbines if t["n_defects"] > 0]
    summary = {
        "n_turbines": len(turbines),
        "turbine_codes": [t["turbine_code"] for t in turbines],
        # 결함 0건 터빈: inspection.turbine_id 로 점검 대상을 알 수 있으므로 실제로 채워진다
        # (결함 0건인 점검이 데이터상 존재한다). 상세 섹션 없이 개요에만 표기된다.
        "zero_defect_turbines": [t["turbine_code"] for t in turbines if t["n_defects"] == 0],
        "n_defects_total": int(len(df)),
        "n_images_total": int(df["image_path"].nunique()) if len(df) else 0,
    }
    if df.empty:
        return {
            **summary,
            "severity_counts": {},
            "max_severity": None,
            "type_counts": {},
            "top_defect_type": None,
            "top_defect_type_n": 0,
            "worst_turbine": None,
            "worst_turbine_n": 0,
            "confidence": {},
        }

    top_type = df["defect_type"].value_counts().idxmax()
    worst = max(with_defect, key=lambda t: t["n_defects"])
    return {
        **summary,
        "severity_counts": {
            str(s): int((df["severity"] == s).sum())
            for s in sorted(df["severity"].unique(), reverse=True)
        },
        "max_severity": int(df["severity"].max()),
        # 개요 차트용 전체 유형 분포. 터빈별 type_counts 와 같은 집계를 전체에 적용.
        "type_counts": _counts(df["defect_type"]),
        "top_defect_type": top_type,
        "top_defect_type_n": int((df["defect_type"] == top_type).sum()),
        "worst_turbine": worst["turbine_code"],
        "worst_turbine_n": worst["n_defects"],
        "confidence": {
            "min": round(float(df["confidence"].min()), 3),
            "mean": round(float(df["confidence"].mean()), 3),
            "max": round(float(df["confidence"].max()), 3),
        },
    }


def fetch(event_id: int) -> dict:
    """inspection_id → tool_outputs.

    (공유 계약) event_id 자리에 inspection_id 가 들어온다. 반환 dict가 그대로
    state['tool_outputs']가 되고 critic 의 검증 기준이 된다.

    반환: {"event": inspection, "turbines": [터빈별 집계], "summary": {개요 요약}}
      - 점검 대상 터빈은 inspection.turbine_id 가 원본이며, 결함이 한 건도 없어도 남는다
        (그 터빈은 n_defects=0 으로 집계되어 summary.zero_defect_turbines 에 들어간다).
      - 조인이 비어(FK 파손) 대상 터빈을 못 찾으면 defect 쪽 코드로 대체한다.
    """
    inspection = get_inspection(event_id)
    if not inspection.get("found"):
        return {"event": inspection}

    df = get_defects(event_id)
    codes = sorted(
        set(df["turbine_code"].dropna().unique())
        | ({inspection["turbine_code"]} if inspection.get("turbine_code") else set())
    )
    turbines = [_aggregate(df[df["turbine_code"] == code], code) for code in codes]

    return {
        "event": inspection,
        "turbines": turbines,
        "summary": _summarize(turbines, df),
    }
