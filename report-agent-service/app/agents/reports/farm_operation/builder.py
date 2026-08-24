"""farm_operation(단지) 결정론적 렌더러 — 모든 수치 코드 주입, 차트 mermaid.

터빈 운영 보고서와 구분되는 단지 고유 구성:
  - 단지 총 발전량(전 터빈 합산) 중심 KPI
  - **터빈별 실적 순위표**(부진 터빈 식별 = 단지 운영의 핵심)
operation.builder의 공용 헬퍼(_f/_bar 등)를 재사용한다(운영 계열 동일 스타일).
"""
from app.agents.verify import extract_numbers
from app.agents.reports.operation.builder import (
    _f, _num1, _pad_display, _bar, _XY_COLORS, _loss_driver, CATEGORY_KO, DISCLAIMER,
)


def facts(to) -> dict:
    sc = to.get("scada", {}) or {}
    a = to.get("anomaly", {}) or {}
    d = to.get("defect", {}) or {}
    by = a.get("by_category", {}) or {}

    def r(v, nd=1):
        return round(v, nd) if isinstance(v, (int, float)) and not isinstance(v, bool) else None

    return {
        "total_actual_mwh": r(sc.get("total_actual_mwh")),
        "total_expected_mwh": r(sc.get("total_expected_mwh")),
        "utilization": r(sc.get("utilization_pct")),
        "availability": r(sc.get("availability_pct")),
        "avg_wind": r(sc.get("avg_wind_speed")),
        "energy_loss_mwh": r(sc.get("energy_loss_mwh")),
        "downtime_loss_mwh": r(sc.get("downtime_loss_mwh")),
        "performance_loss_mwh": r(sc.get("performance_loss_mwh")),
        "n_turbines": int(sc.get("n_turbines", 0)),
        "total_events": int(a.get("total", 0)),
        "ongoing": int(a.get("ongoing", 0)),
        "ended": int(a.get("ended", 0)),
        "stop": int(by.get("stop", 0)),
        "data_missing": int(by.get("data_missing", 0)),
        "degradation": int(by.get("degradation", 0)),
        "defect_count": int(d.get("count", 0)),
        "defect_available": bool(d.get("available", False)),
        "n_inspections": int(d.get("n_inspections", 0)),
        "high_severity": int(d.get("high_severity", 0)),
    }


def _worst_turbine(to):
    pt = (to.get("scada", {}) or {}).get("per_turbine", [])
    return pt[0]["turbine_code"] if pt else "—"


def fact_lines(to) -> list:
    """LLM 종합분석이 '인용'할 수 있는 facts 블록(수치 포함). 이 수치만 인용 가능(생성 금지)."""
    f = facts(to)
    crit, low, ok, na, top_loss = _fleet_health(to)
    lines = [
        f"- 단지 총 실측 발전량: {_f(f['total_actual_mwh'])} MWh / 총 기대 발전량: {_f(f['total_expected_mwh'])} MWh",
        f"- 단지 발전 달성률(실측/기대): {_f(f['utilization'])}%",
        f"- 단지 가동률: {_f(f['availability'])}%, 관측 평균 풍속: {_f(f['avg_wind'])} m/s",
        f"- 총 손실 {_f(f['energy_loss_mwh'])} MWh 중 정지 {_f(f['downtime_loss_mwh'])} / "
        f"성능저하 {_f(f['performance_loss_mwh'])} MWh",
        f"- 이상 이벤트 {f['total_events']}건 (정지 {f['stop']}·데이터부재 {f['data_missing']}·성능저하 {f['degradation']})",
        f"- 실적 최저 터빈: {_worst_turbine(to)} / 손실 기여 최대 터빈: {top_loss}",
        f"- 함대 건강: 심각 {crit}기 / 저조 {low}기 / 정상 {ok}기",
    ]
    return lines


def allowed_numbers(to) -> set:
    """종합분석이 인용해도 되는 수치(절대값) 집합 = facts 블록 + 기간 날짜 + 터빈 코드 번호.

    터빈 코드는 'U5'로 쓰면 extract_numbers가 걸러내지만, LLM이 '터빈 5'로 풀어 쓰면 숫자 5가
    추출돼 환각으로 오반려된다. 코드 번호(U5→5)를 허용집합에 넣어 식별자 언급을 막지 않는다.
    """
    text = "\n".join(fact_lines(to))
    t = to.get("farm", {}) or {}
    text += "\n" + str(t.get("period_start", "")) + "\n" + str(t.get("period_end", ""))
    allowed = {abs(n) for n in extract_numbers(text)}
    for tr in (to.get("scada", {}) or {}).get("per_turbine", []):
        digits = "".join(ch for ch in str(tr.get("turbine_code", "")) if ch.isdigit())
        if digits:
            allowed.add(float(digits))
    return allowed


def build_kpi_table(to) -> list:
    f = facts(to)
    achieve = f["utilization"] or 0
    status = "정상 가동" if achieve >= 80 else ("저조 가동" if achieve >= 40 else "심각 — 단지 실적 부진")
    return [
        "| 지표 | 값 |", "|---|---|",
        f"| 대상 터빈 | {f['n_turbines']}기 |",
        f"| 단지 총 발전량 (실측 / 기대) | {_f(f['total_actual_mwh'])} / {_f(f['total_expected_mwh'])} MWh |",
        f"| 발전 달성률 (실측/기대) | {_f(f['utilization'])}% |",
        f"| 단지 가동률 (Availability) | {_f(f['availability'])}% |",
        f"| 총 손실 발전량 | {_f(f['energy_loss_mwh'])} MWh |",
        f"| 관측 평균 풍속 | {_f(f['avg_wind'])} m/s |",
        f"| 이상 이벤트 (진행 중) | {f['total_events']}건 ({f['ongoing']}건) |",
        f"| 결함 | {f['defect_count']}건" + ("" if f["defect_available"] else " (미연동)") + " |",
        f"| 실적 최저 터빈 | {_worst_turbine(to)} |",
        f"| 종합 가동 상태 | {status} |",
    ]


def _mermaid_monthly(monthly) -> list:
    if not monthly:
        return ["- 월별 데이터 없음"]
    months = [m["month"][2:] for m in monthly]
    exp = [_num1(m.get("expected")) for m in monthly]
    act = [_num1(m.get("actual")) for m in monthly]
    top = (int(max(exp + [0]) // 50) + 1) * 50
    bot = int(min(act + [0]) // 50) * 50
    return [
        "🔵 기대 · 🟠 실측 (단지 월별 총 발전량, MWh)",
        "",
        "```mermaid",
        f'%%{{init: {{"themeVariables": {{"xyChart": {{"plotColorPalette": "{_XY_COLORS}"}}}}}}}}%%',
        "xychart-beta",
        '    title "단지 월별 기대 vs 실측 발전량 (MWh)"',
        f"    x-axis [{', '.join(months)}]",
        f'    y-axis "MWh" {bot} --> {top}',
        f"    line [{', '.join(str(v) for v in exp)}]",
        f"    line [{', '.join(str(v) for v in act)}]",
        "```",
    ]


def build_generation_section(to) -> list:
    sc = to.get("scada", {}) or {}
    if not sc.get("found"):
        return ["- 발전 실적 데이터 없음"]
    return [
        f"- 단지 총 실측 발전량: {_f(sc.get('total_actual_mwh'))} MWh",
        f"- 단지 총 기대 발전량: {_f(sc.get('total_expected_mwh'))} MWh",
        f"- 발전 달성률(실측/기대): {_f(sc.get('utilization_pct'))}%",
        "",
        *_mermaid_monthly(sc.get("monthly", [])),
    ]


def build_loss_diagnosis(to) -> list:
    f = facts(to)
    dt, pf = f["downtime_loss_mwh"] or 0, f["performance_loss_mwh"] or 0
    denom = dt + pf
    dt_s = (dt / denom * 100) if denom else 0
    pf_s = (pf / denom * 100) if denom else 0
    driver, _ = _loss_driver(f)
    return [
        f"- 단지 총 손실 발전량: {_f(f['energy_loss_mwh'])} MWh (기대−실측)",
        f"- ├ 정지로 인한 손실: {_f(dt)} MWh ({dt_s:.0f}%)",
        f"- └ 가동 중 성능저하 손실: {_f(pf)} MWh ({pf_s:.0f}%)",
        f"- 단지 가동률: {_f(f['availability'])}%  ·  관측 평균 풍속: {_f(f['avg_wind'])} m/s",
        f"- **주 손실 요인**: {driver}",
    ]


def _fleet_health(to):
    """(심각, 저조, 정상, 미가용) 터빈 수 + 최대 손실 기여 터빈. 달성률 임계 40/80.

    달성률이 None(기대발전량 0 등으로 산출 불가)인 터빈은 '미가용'으로 분리한다
    — 0으로 간주하면 '심각'으로 오분류돼 함대 건강·LLM 총평 근거가 왜곡된다.
    """
    pt = (to.get("scada", {}) or {}).get("per_turbine", [])
    vals = [t.get("utilization_pct") for t in pt]
    crit = sum(1 for v in vals if v is not None and v < 40)
    low = sum(1 for v in vals if v is not None and 40 <= v < 80)
    ok = sum(1 for v in vals if v is not None and v >= 80)
    na = sum(1 for v in vals if v is None)
    top_loss = max(pt, key=lambda t: (t.get("loss_mwh") or 0), default=None)
    return crit, low, ok, na, (top_loss["turbine_code"] if top_loss else "—")


def build_fleet_health(to) -> list:
    """함대 건강 요약 — 심각/저조/정상(+미가용) 몇 기 (국소 vs 전반 문제 신호)."""
    f = facts(to)
    crit, low, ok, na, _ = _fleet_health(to)
    line = (f"- 전체 {f['n_turbines']}기 중 — 🔴 심각(달성률<40%) {crit}기 · "
            f"🟡 저조(40~80%) {low}기 · 🟢 정상(≥80%) {ok}기")
    if na:
        line += f" · ⚪ 산출 불가 {na}기"
    return [line]


def build_turbine_table(to) -> list:
    """터빈별 실적·손실 기여도 (손실 큰 순) — 단지 보고서 핵심(조치 우선순위)."""
    pt = list((to.get("scada", {}) or {}).get("per_turbine", []))
    if not pt:
        return ["- 터빈별 데이터 없음"]
    farm_loss = facts(to)["energy_loss_mwh"] or 0
    pt.sort(key=lambda t: -(t.get("loss_mwh") or 0))   # 손실 큰 순 = 조치 우선
    lines = ["| 순위 | 터빈 | 실측(MWh) | 달성률 | 가동률 | 손실(MWh) | 손실 기여율 |",
             "|---|---|---|---|---|---|---|"]
    for i, t in enumerate(pt, 1):
        loss = t.get("loss_mwh") or 0
        share = (loss / farm_loss * 100) if farm_loss else 0
        lines.append(
            f"| {i} | {t['turbine_code']} | {_f(t.get('actual_mwh'))} | "
            f"{_f(t.get('utilization_pct'))}% | {_f(t.get('availability_pct'))}% | "
            f"{_f(loss)} | {share:.0f}% |")
    return lines


def build_event_distribution(to) -> list:
    """이상 이벤트 터빈별 분포 (많은 순) — 정지·저하가 어느 터빈에 몰렸나."""
    bt = (to.get("anomaly", {}) or {}).get("by_turbine", [])
    if not bt:
        return ["- 이상 이벤트 없음"]
    lines = ["| 터빈 | 이상 건수 | 정지 | 데이터 부재 | 성능 저하 |", "|---|---|---|---|---|"]
    for t in bt:
        lines.append(f"| {t['turbine_code']} | {t['total']} | {t['stop']} | "
                     f"{t['data_missing']} | {t['degradation']} |")
    return lines


def build_status_and_bars(to) -> list:
    f = facts(to)
    order = [("stop", f["stop"]), ("data_missing", f["data_missing"]), ("degradation", f["degradation"])]
    mx = max([c for _, c in order] + [0])
    bars = ["```"]
    for k, c in order:
        bars.append(f"{_pad_display(CATEGORY_KO[k], 12)} | {_bar(c, mx)} {c}")
    bars.append("```")
    return [
        "가동 상태별 (유지 중 / 종료):", "",
        "| 가동 상태 | 건수 |", "|---|---|",
        f"| 유지 중(진행) | {f['ongoing']} |",
        f"| 종료(복구) | {f['ended']} |",
        "",
        "저해 유형별 (정지 / 데이터 부재 / 성능 저하):", "",
        *bars,
    ]


def build_defect_section(to) -> list:
    """결함 및 유지보수 — 드론 점검 횟수 / 신규 결함 수 / 고위험 결함 유무."""
    d = to.get("defect", {}) or {}
    if not d.get("available"):
        return ["- 결함 진단 데이터 미연동 — 결함 건수 산출 불가 (0건으로 표기)"]
    high = int(d.get("high_severity", 0))
    lines = [
        f"- 드론 점검 횟수: {d.get('n_inspections', 0)}회",
        f"- 신규 발견 결함: {d.get('count', 0)}건",
        f"- 고위험 결함(심각도 3 이상): {high}건" + (" ⚠️" if high else " — 없음"),
    ]
    by_type = d.get("by_type") or {}
    if by_type:
        lines.append("- 주요 결함 유형: " + ", ".join(f"{k} {v}건" for k, v in by_type.items()))
    return lines


def render_report(to, analysis: str = None) -> str:
    t = to.get("farm", {}) or {}
    f = facts(to)
    farm = t.get("farm_name", "발전소")
    start, end = t.get("period_start", "—"), t.get("period_end", "—")
    _, action = _loss_driver(f)

    parts = [
        f"# {farm} {start} ~ {end} 운영보고서",
        "",
        f"**발전소** {farm}  |  **대상 터빈** {f['n_turbines']}기  |  "
        f"**보고서 유형** 단지 운영(wind_farm_operation)",
        "",
        "---",
        "",
        "## Ⅰ. 핵심 지표 (KPI)",
        "",
        *build_kpi_table(to),
        "",
        "## Ⅱ. 발전 성과",
        *build_generation_section(to),
        "",
        "## Ⅲ. 발전 손실 분해 진단",
        *build_loss_diagnosis(to),
        "",
        "## Ⅳ. 터빈별 실적·손실 기여 (손실 큰 순)",
        "",
        *build_fleet_health(to),
        "",
        *build_turbine_table(to),
        "",
        "## Ⅴ. 가동 저해 현황",
        "",
        *build_status_and_bars(to),
        "",
        "이상 이벤트 터빈별 분포 (많은 순):",
        "",
        *build_event_distribution(to),
    ]
    if analysis:
        parts += ["", "## Ⅵ. 단지 운영 총평", analysis.strip()]
    parts += [
        "",
        "## 결함 점검 현황",
        *build_defect_section(to),
        "",
        "## 운영 조치 권고",
        f"- {action}",
        "",
        "---",
        f"_※ {DISCLAIMER}_",
    ]
    return "\n".join(parts) + "\n"
