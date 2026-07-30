"""operation 결정론적 렌더러 — 모든 수치는 코드가 tool_outputs에서 주입(사실의 단일 출처).

팀 규칙(anomaly/defect와 동일):
- 표·차트·수치는 전부 builder(코드)가 만든다(사실의 단일 출처).
- render_report(to, analysis=None): analysis(선택)는 facts 수치를 '인용'한 종합 총평. critic이 grounding으로 검증.
- fact_lines/allowed_numbers: LLM 인용 대상·검증 허용집합.
- 차트는 mermaid xychart-beta (anomaly와 동일 컨벤션, GitHub에서 렌더/ git 친화적).

운영 리포트 고유 포맷(이상감지와 구분): KPI 대시보드 → 발전 성과 → 손실 분해 진단 →
가동 저해 현황 → (총평) → 결함 → 조치.
"""
from app.agents.verify import extract_numbers

CATEGORY_KO = {"stop": "정지", "data_missing": "데이터 부재", "degradation": "성능 저하"}
DISCLAIMER = "본 보고서는 관측 사실과 운영 지표만 제시합니다. 원인 판단·조치 결정은 담당자 확인이 필요합니다."

# mermaid xychart 기본 팔레트 저대비 → 색 명시 (파랑=기대, 주황=실측)
_XY_COLORS = "#2a78d6, #eb6834"


def _f(v, nd=1):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return "—"
    return f"{v:,.{nd}f}"


def _num1(v):
    return round(v, 1) if isinstance(v, (int, float)) and not isinstance(v, bool) else 0


def _pad_display(s, cols):
    """전각(한글=2칸) 고려 우측 공백 패딩 (막대 라벨 정렬)."""
    w = sum(2 if ord(ch) > 0x1100 else 1 for ch in s)
    return s + " " * max(0, cols - w)


def _bar(count, max_count, width=20):
    if not max_count or count <= 0:
        return ""
    return "█" * max(1, round(count / max_count * width))


# ── fact 딕셔너리 (표시값) ─────────────────────────────────────────────────
def facts(to) -> dict:
    a = to.get("anomaly", {}) or {}
    sc = to.get("scada", {}) or {}
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
        "total_events": int(a.get("total", 0)),
        "ongoing": int(a.get("ongoing", 0)),
        "ended": int(a.get("ended", 0)),
        "stop": int(by.get("stop", 0)),
        "data_missing": int(by.get("data_missing", 0)),
        "degradation": int(by.get("degradation", 0)),
        "defect_count": int(d.get("count", 0)),
        "defect_available": bool(d.get("available", False)),
    }


def fact_lines(to) -> list:
    """LLM 종합분석이 '인용'할 수 있는 facts 블록(수치 포함). 이 수치만 인용 가능(생성 금지)."""
    f = facts(to)
    lines = []
    if to.get("scada", {}).get("found"):
        lines += [
            f"- 총 실측 발전량: {_f(f['total_actual_mwh'])} MWh / 총 기대 발전량: {_f(f['total_expected_mwh'])} MWh",
            f"- 발전 달성률(실측/기대): {_f(f['utilization'])}%",
            f"- 가동률(정지 안 한 시간 비율): {_f(f['availability'])}%",
            f"- 관측 평균 풍속: {_f(f['avg_wind'])} m/s",
            f"- 총 손실 {_f(f['energy_loss_mwh'])} MWh 중 정지손실 {_f(f['downtime_loss_mwh'])} / "
            f"성능저하손실 {_f(f['performance_loss_mwh'])} MWh",
        ]
    lines.append(
        f"- 이상 이벤트 {f['total_events']}건(유지중 {f['ongoing']}/종료 {f['ended']}), "
        f"정지 {f['stop']}·데이터부재 {f['data_missing']}·성능저하 {f['degradation']}")
    return lines


def allowed_numbers(to) -> set:
    """종합분석이 인용해도 되는 수치(절대값) 집합 = facts 블록 + 기간 날짜.

    critic grounding: 분석의 모든 숫자가 이 집합에 있어야 통과(없으면 환각/변형). anomaly와 동일.
    """
    text = "\n".join(fact_lines(to))
    t = to.get("turbine", {}) or {}
    text += "\n" + str(t.get("period_start", "")) + "\n" + str(t.get("period_end", ""))
    return {abs(n) for n in extract_numbers(text)}


def _loss_driver(f) -> tuple:
    """(주 손실 요인 label, 조치 문구). 정지 vs 성능저하 비교."""
    dt = f["downtime_loss_mwh"] or 0
    pf = f["performance_loss_mwh"] or 0
    if (dt + pf) <= 0:
        return "특이 손실 없음", "특이 손실 없음 — 정기 모니터링 유지."
    if dt >= pf:
        return "정지(가동률)", (
            f"가동률 개선 우선 — 정지 손실 {_f(dt)} MWh가 주 요인. 정지 원인 규명·복구로 가동 시간 확보"
            + (" (진행 중 이벤트 즉시 확인)." if f["ongoing"] > 0 else "."))
    return "가동 중 성능저하", (
        f"성능 점검 우선 — 성능저하 손실 {_f(pf)} MWh가 주 요인. 출력곡선·피치·요 정렬 점검 권고.")


# ── 코드 주입 섹션 ─────────────────────────────────────────────────────────
def build_kpi_table(to) -> list:
    f = facts(to)
    achieve = f["utilization"] or 0
    status = "정상 가동" if achieve >= 80 else ("저조 가동" if achieve >= 40 else "심각 — 발전 실적 부진")
    return [
        "| 지표 | 값 |", "|---|---|",
        f"| 총 발전량 (실측 / 기대) | {_f(f['total_actual_mwh'])} / {_f(f['total_expected_mwh'])} MWh |",
        f"| 발전 달성률 (실측/기대) | {_f(f['utilization'])}% |",
        f"| 가동률 (Availability) | {_f(f['availability'])}% |",
        f"| 총 손실 발전량 | {_f(f['energy_loss_mwh'])} MWh |",
        f"| 관측 평균 풍속 | {_f(f['avg_wind'])} m/s |",
        f"| 이상 이벤트 (진행 중) | {f['total_events']}건 ({f['ongoing']}건) |",
        f"| 결함 | {f['defect_count']}건" + ("" if f["defect_available"] else " (미연동)") + " |",
        f"| 종합 가동 상태 | {status} |",
    ]


def _mermaid_monthly(monthly) -> list:
    if not monthly:
        return ["- 월별 데이터 없음"]
    months = [m["month"][2:] for m in monthly]   # 'YY-MM'
    exp = [_num1(m.get("expected")) for m in monthly]
    act = [_num1(m.get("actual")) for m in monthly]
    top = (int(max(exp + [0]) // 100) + 1) * 100
    bot = int(min(act + [0]) // 100) * 100
    return [
        "🔵 기대(expected_power_unit) · 🟠 실측(power_output)",
        "",
        "```mermaid",
        f'%%{{init: {{"themeVariables": {{"xyChart": {{"plotColorPalette": "{_XY_COLORS}"}}}}}}}}%%',
        "xychart-beta",
        '    title "월별 기대 vs 실측 발전량 (kW)"',
        f"    x-axis [{', '.join(months)}]",
        f'    y-axis "kW" {bot} --> {top}',
        f"    line [{', '.join(str(v) for v in exp)}]",
        f"    line [{', '.join(str(v) for v in act)}]",
        "```",
    ]


def build_generation_section(to) -> list:
    sc = to.get("scada", {}) or {}
    if not sc.get("found"):
        return ["- 발전 실적 데이터 없음"]
    return [
        f"- 총 실측 발전량: {_f(sc.get('total_actual_mwh'))} MWh",
        f"- 총 기대 발전량: {_f(sc.get('total_expected_mwh'))} MWh",
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
        f"- 총 손실 발전량: {_f(f['energy_loss_mwh'])} MWh (기대−실측)",
        f"- ├ 정지로 인한 손실: {_f(dt)} MWh ({dt_s:.0f}%)",
        f"- └ 가동 중 성능저하 손실: {_f(pf)} MWh ({pf_s:.0f}%)",
        f"- 가동률: {_f(f['availability'])}%  ·  관측 평균 풍속: {_f(f['avg_wind'])} m/s",
        f"- **주 손실 요인**: {driver}",
    ]


def build_status_table(to) -> list:
    f = facts(to)
    return [
        "| 가동 상태 | 건수 |", "|---|---|",
        f"| 유지 중(진행) | {f['ongoing']} |",
        f"| 종료(복구) | {f['ended']} |",
    ]


def build_type_bars(to) -> list:
    f = facts(to)
    order = [("stop", f["stop"]), ("data_missing", f["data_missing"]), ("degradation", f["degradation"])]
    mx = max([c for _, c in order] + [0])
    lines = ["```"]
    for k, c in order:
        lines.append(f"{_pad_display(CATEGORY_KO[k], 12)} | {_bar(c, mx)} {c}")
    lines.append("```")
    return lines


def build_defect_section(to) -> list:
    d = to.get("defect", {}) or {}
    if not d.get("available"):
        return ["- 결함 진단 데이터 미연동 — 결함 건수 산출 불가 (0건으로 표기)"]
    return [f"- 기간 내 결함 건수: {d.get('count', 0)}건"]


def render_report(to, analysis: str = None) -> str:
    """전체 조립. 코드가 모든 수치 주입 + (선택) 숫자 없는 총평 삽입."""
    t = to.get("turbine", {}) or {}
    f = facts(to)
    farm = t.get("farm_name", "발전소")
    code = t.get("turbine_code", "—")
    start, end = t.get("period_start", "—"), t.get("period_end", "—")
    _, action = _loss_driver(f)

    parts = [
        f"# {farm} 터빈 {code} {start} ~ {end} 운영보고서",
        "",
        f"**발전소** {farm}  |  **터빈** {code}  |  **보고서 유형** 터빈 운영(turbine_operation)",
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
        "## Ⅳ. 가동 저해 현황",
        "",
        "가동 상태별 (유지 중 / 종료):",
        "",
        *build_status_table(to),
        "",
        "저해 유형별 (정지 / 데이터 부재 / 성능 저하):",
        "",
        *build_type_bars(to),
    ]
    if analysis:
        parts += ["", "## Ⅴ. 운영 총평", analysis.strip()]
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
