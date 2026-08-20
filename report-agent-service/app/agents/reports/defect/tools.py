"""defect 데이터 조회 — LLM 절대 사용 안 함.

반환 수치는 전부 원본 집계 그대로(임의 가공 금지). 이 값들이 critic의 검증 기준이 된다.

보고서의 단위는 report 다(report.csv). 점검(inspection)은 터빈 1대당 1건이고,
드론 1회 출동으로 2대를 점검하면 inspection 2건이 같은 report_id 를 공유한다
(실데이터 60개 보고서 중 8개가 터빈 2대). 그래서 fetch 는 report_id 로 조회하고,
그 아래 inspection 전부의 결함을 모아 터빈별로 집계한다.
  ※ defect 의 event_id 는 report_id 다 — inspection_id 가 아니다.
     (event_id 해석은 원래 report_type 마다 다르다: operation 은 터빈번호, farm_operation 은 무시.)

ERD상 각 테이블은 코드값을 직접 갖지 않고 FK만 갖는다 → _load()에서 조인해 붙인다.
  report.report_id     ← inspection.report_id                              (보고서 1건 = 점검 N건)
  inspection.turbine_id → turbine.turbine_code / wind_farm.wind_farm_name  (점검 대상 터빈)
  defect.blade_id       → blade.blade_tag / blade.turbine_id → turbine.turbine_code
점검 대상 터빈은 inspection.turbine_id 로 확정되므로 결함 0건이어도 알 수 있다.

데이터 출처는 app.core.datasource 가 테이블 단위로 정한다. report·inspection·defect 는
_RDS_TABLES 에 등록돼 있어 RDS 에서 읽는다(예전에는 CSV 였다). 참조표(turbine·blade·wind_farm)만
캐시되고 사실 테이블은 매번 조회된다 — 여러 테이블의 시점 일관성은 service 의 snapshot()
트랜잭션이 보장한다(operation·anomaly 도 같은 방식).

(공유 계약) 반환 dict의 "event" 키는 공유 service.py 가 found 판정에 쓰는 자리다.
defect 에서는 그 자리에 report 1건(+소속 점검 요약)이 들어간다.
"""
import pandas as pd

from app.core.datasource import load_table, turbine_index

# 검출 신뢰도 하한. None이면 필터 없음(현재 데이터 최저 0.684).
# 임계 정책이 정해지면 이 값만 바꾸면 모든 집계에 일괄 반영된다.
MIN_CONFIDENCE = None

# 조인이 비어(blade→turbine FK 파손) 터빈을 특정하지 못한 결함을 담는 자리표시자.
# 이런 행을 조용히 빼면 총건수와 터빈별 합계가 어긋나므로, 한 그룹으로 묶어 보고서에 드러낸다.
UNKNOWN_TURBINE = "터빈 미상"


def _join_inspection():
    """inspection + 터빈/발전소 이름."""
    return load_table("inspection").merge(turbine_index(), on="turbine_id", how="left")


def _join_defect():
    """defect + 블레이드 태그 + 터빈 코드."""
    blade = load_table("blade")[["blade_id", "blade_tag", "turbine_id"]]
    # how="left" — FK가 깨져도 행을 잃지 않는다. 코드가 비면 집계에서 걸러진다(fetch 참고).
    return (
        load_table("defect")
        .merge(blade, on="blade_id", how="left")
        .merge(turbine_index()[["turbine_id", "turbine_code"]], on="turbine_id", how="left")
    )


def _load():
    """지연 로드 + ERD 조인. anomaly만 실행할 때 defect 테이블을 읽지 않도록 lazy.

    조인 결과로 inspection 에는 turbine_code·wind_farm_id·wind_farm_name 이,
    defect 에는 turbine_id·turbine_code·blade_tag 가 붙는다. 아래 집계 함수들은
    이 컬럼들이 처음부터 있었던 것처럼 그대로 쓴다(조인 위치는 여기 한 곳뿐).

    조인 결과를 캐시하지 않는다. cached() 는 TTL 이 없어(프로세스 수명) 사실 테이블을 담으면
    컨테이너 기동 이후에 만들어진 점검·결함이 영영 보이지 않는다 — 실제로 그 때문에 실재하는
    보고서가 404 로 떨어졌다(#153). 시점 일관성은 캐시가 아니라 service 의 snapshot() 트랜잭션이
    맡는다. 참조표(turbine_index)는 보고서 도중 바뀌지 않으므로 datasource 가 캐시해도 안전하다.

    반환: (report, inspection, defect)
    """
    return (
        load_table("report"),
        _join_inspection(),
        _join_defect(),
    )


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


# 점검 상태 진행 순서. 한 보고서에 점검이 여러 건이면 '가장 덜 진행된' 상태로 대표한다
# — 하나라도 판독 전이면 보고서 전체를 미완으로 봐야 안전하다(builder가 단서 문구를 붙인다).
_STATUS_ORDER = ("UPLOADING", "INSPECTING", "INSPECTED")


def _rollup_status(values) -> str:
    """점검 여러 건의 status → 보고서 대표 status."""
    vals = [v for v in values if isinstance(v, str)]
    if not vals:
        return None
    # 모르는 상태값은 숨기지 않고 그대로 노출한다(조용히 INSPECTED로 뭉개지 않기 위해).
    unknown = sorted(v for v in vals if v not in _STATUS_ORDER)
    return unknown[0] if unknown else min(vals, key=_STATUS_ORDER.index)


def get_report(report_id: int) -> dict:
    """report 1건 + 소속 inspection 요약 → dict. 없으면 {'found': False}.

    turbine_code·wind_farm_* 는 _load()의 조인이 붙여준 값이다(원본 테이블에는 FK만 있음).
    점검이 하나도 안 달린 보고서는 렌더링할 내용이 없으므로 found=False 로 돌려준다
    (호출자는 404를 받는다 — 빈 보고서를 만들어 내보내는 것보다 낫다).

    실데이터 기준 한 보고서 안에서는 발전소·상태·시작시각이 모두 동일하지만,
    아래는 그 가정에 기대지 않고 집계한다(기간은 min~max, 상태는 _rollup_status).
    """
    rep, insp, _ = _load()
    row = rep[rep["report_id"] == report_id]
    if row.empty:
        return {"found": False}
    sub = insp[insp["report_id"] == report_id].sort_values("inspection_id")
    if sub.empty:
        return {"found": False}

    r, first = row.iloc[0], sub.iloc[0]
    return {
        "found": True,
        "report_id": int(r["report_id"]),
        "report_type": _num(r["report_type"]),
        "report_status": _num(r["status"]),
        # 발전소는 보고서 단위로 하나. 첫 점검 기준(위 docstring 참고).
        "wind_farm_id": _num(first["wind_farm_id"]),
        "wind_farm_name": _num(first["wind_farm_name"]),
        "n_inspections": int(len(sub)),
        "inspection_ids": [int(x) for x in sub["inspection_id"]],
        "turbine_codes": [c for c in sub["turbine_code"] if isinstance(c, str)],
        # 점검이 여러 건이면 전체를 감싸는 구간으로.
        "inspection_start": _num(sub["inspection_start"].min()),
        "inspection_end": _num(sub["inspection_end"].max()),
        "user_id": _num(first["user_id"]),
        "status": _rollup_status(sub["status"]),
    }


def get_defects(report_id: int):
    """해당 보고서(소속 inspection 전부)의 결함 행(DataFrame). MIN_CONFIDENCE 적용."""
    _, insp, defect = _load()
    ids = insp[insp["report_id"] == report_id]["inspection_id"]
    df = defect[defect["inspection_id"].isin(ids)]
    if MIN_CONFIDENCE is not None:
        df = df[df["confidence"] >= MIN_CONFIDENCE]
    return df


def _image_seq(df) -> dict:
    """(blade_tag, part_side)별로 이미지에 1부터 순번 → {image_path: seq}.

    파일명이 아니라 컬럼 기준으로 센다 — 파일명의 '..._A_LeadingEdge_002' 표기는
    실제 blade_tag/part_side 와 일치하지 않아 그대로 믿으면 엉뚱한 부위로 적힌다.

    보고서 캡션에는 더 이상 쓰지 않는다(부위당 1장이라 항상 1이고, 이미지가 순서대로
    렌더링되므로 글자로 반복할 필요가 없다). tool_outputs 의 images[].seq 로만 남겨
    프론트가 자체 갤러리를 만들 때 쓸 수 있게 한다.
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
    # with_defect 는 df 가 비지 않으면 보통 채워지지만, 빈 경우에도 죽지 않게 둔다
    # (max([]) 는 ValueError 라 보고서 생성이 통째로 중단된다).
    worst = max(with_defect, key=lambda t: t["n_defects"]) if with_defect else None
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
        "worst_turbine": worst["turbine_code"] if worst else None,
        "worst_turbine_n": worst["n_defects"] if worst else 0,
        "confidence": {
            "min": round(float(df["confidence"].min()), 3),
            "mean": round(float(df["confidence"].mean()), 3),
            "max": round(float(df["confidence"].max()), 3),
        },
    }


def fetch(event_id: int) -> dict:
    """report_id → tool_outputs.

    (공유 계약) event_id 자리에 report_id 가 들어온다 — inspection_id 가 아니다.
    반환 dict가 그대로 state['tool_outputs']가 되고 critic 의 검증 기준이 된다.

    반환: {"event": report, "turbines": [터빈별 집계], "summary": {개요 요약}}
      - 보고서에 점검이 2건 묶이면 터빈도 2대가 되고, turbines 가 2개로 나온다.
      - 점검 대상 터빈은 inspection.turbine_id 가 원본이며, 결함이 한 건도 없어도 남는다
        (그 터빈은 n_defects=0 으로 집계되어 summary.zero_defect_turbines 에 들어간다).
      - 터빈을 못 찾은 결함(FK 파손)은 버리지 않고 UNKNOWN_TURBINE 그룹으로 모은다.
        버리면 n_defects_total(=len(df))과 터빈별 합계가 어긋나고, 전부 미상이면
        터빈 집계가 하나도 안 생겨 _summarize 의 max(with_defect) 가 터진다.
    """
    report = get_report(event_id)
    if not report.get("found"):
        return {"event": report}

    df = get_defects(event_id)
    # 미상 결함도 한 그룹으로 세어야 '총 N건'과 터빈별 합계가 맞는다. 조용히 빠지면
    # 보고서에 합이 안 맞는 숫자가 실리고, critic 이 그걸 근거로 검증하게 된다.
    grouped = df["turbine_code"].fillna(UNKNOWN_TURBINE) if len(df) else df.get("turbine_code")
    codes = sorted(set(grouped.unique()) | set(report["turbine_codes"])) if len(df) \
        else sorted(set(report["turbine_codes"]))
    turbines = [_aggregate(df[grouped == code] if len(df) else df, code) for code in codes]

    return {
        "event": report,
        "turbines": turbines,
        "summary": _summarize(turbines, df),
    }
