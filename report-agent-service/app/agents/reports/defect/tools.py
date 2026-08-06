"""defect 데이터 조회 — LLM 절대 사용 안 함.

반환 수치는 전부 원본 집계 그대로(임의 가공 금지). 이 값들이 critic의 검증 기준이 된다.
데이터: inspection.csv(점검 1건 = 드론 출동 1회) + defect.csv(CNN 검출 결함 1건 = 1행).
한 inspection이 여러 터빈을 커버하며, 터빈 구분은 defect.turbine_code 로만 알 수 있다.

배포(RDS) 전환: common.config.DATA_SOURCE == "rds" 이면 _load()를 SQLAlchemy 쿼리로
교체(반환 컬럼·시그니처는 동일 유지). fetch()·graph·agent·critic 은 불변.

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


def _load():
    """CSV 지연 로드(1회 캐시). anomaly만 실행할 때 defect CSV를 읽지 않도록 lazy."""
    if not _cache:
        _cache["inspection"] = pd.read_csv(
            os.path.join(DATA_DIR, "inspection.csv"),
            encoding="utf-8-sig",
            parse_dates=["inspection_start", "inspection_end", "created_at"],
        )
        _cache["defect"] = pd.read_csv(
            os.path.join(DATA_DIR, "defect.csv"),
            encoding="utf-8-sig",
            parse_dates=["created_at"],
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
    """inspection 1건 → dict. 없으면 {'found': False}."""
    insp, _ = _load()
    row = insp[insp["inspection_id"] == inspection_id]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    return {
        "found": True,
        "inspection_id": int(r["inspection_id"]),
        "windfarm_id": _num(r["windfarm_id"]),
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
        # 결함 0건 터빈: 현재 스키마에서는 defect 행이 있어야 터빈의 존재를 알 수 있어
        # 실제로는 항상 비어 있다. '점검 대상 터빈' 목록이 생기면 자동으로 채워진다.
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
      - 터빈은 defect.turbine_code 로 식별하며 코드순 정렬.
      - 결함이 한 건도 없는 점검이면 turbines 는 빈 리스트(개요만 렌더링).
    """
    inspection = get_inspection(event_id)
    if not inspection.get("found"):
        return {"event": inspection}

    df = get_defects(event_id)
    turbines = [
        _aggregate(df[df["turbine_code"] == code], code)
        for code in sorted(df["turbine_code"].unique())
    ]

    return {
        "event": inspection,
        "turbines": turbines,
        "summary": _summarize(turbines, df),
    }
