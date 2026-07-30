"""결정론 렌더러 (LLM 없음) — 표 + 유니코드/mermaid 차트 + 권고 조치 + 판단 유보.

'## 종합분석' 섹션에는 LLM이 생성한 서술(수치 grounding 통과)이 들어간다.
모든 표·차트 수치는 코드가 tool_outputs에서 주입(단일 출처).
"""
import pandas as pd

from app.core.config import (
    RECENT_HISTORY_MONTHS, CHRONIC_NATURAL_VAR_PP, CHRONIC_CONFIRM_THRESHOLD_PP, CHRONIC_DAYS,
)
from app.agents.verify import extract_numbers

EVENT_TYPE_KO = {
    "prolonged_stop": "발전 정지(24시간 이상 연속)",
    "data_missing": "데이터 부재(6시간 이상 연속)",
    "chronic_screening": "만성 성능저하 경고",
    "chronic_confirmed": "만성 성능저하 확정",
}

ACTION_BY_TYPE = {
    "prolonged_stop": ("운영팀 즉시 확인 — 발전 손실 진행 중. "
                       "출력제어(계통 지시) 여부부터 확인 — 데이터상 고장 정지와 출력제어가 구분되지 않음."),
    "data_missing": "시스템팀 확인 — 관측 자체가 불가한 상태.",
    "chronic_confirmed": "드론 출동 권고 — 성능 저하 확정, 외관·현장 확인.",
    "chronic_screening": "드론 검토 — 성능 저하 의심, 추이 관찰.",
}

SEVERITY = {"prolonged_stop": "🔴", "chronic_confirmed": "🟠", "chronic_screening": "🟡", "data_missing": "🔵"}

DISCLAIMER = "본 보고서는 관측 사실과 정황만 제시합니다. 원인 판단·조치 결정은 담당자 확인이 필요합니다."

_XY_COLORS = "#3b82f6, #ef4444"
_XY_COLOR_ONE = "#ef4444"


def _f(v, nd=2):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return "—"
    return f"{v:,.{nd}f}"


def _num1(v):
    return round(v, 1) if isinstance(v, (int, float)) and not isinstance(v, bool) else 0


def _init(palette):
    return f'%%{{init: {{"themeVariables": {{"xyChart": {{"plotColorPalette": "{palette}"}}}}}}}}%%'


def _mermaid_profile(series):
    xs = ", ".join(f'"{str(p.get("t"))[:2]}"' for p in series)
    exp = [_num1(p.get("expected")) for p in series]
    act = [_num1(p.get("actual")) for p in series]
    top = (int(max(exp + [0]) // 100) + 1) * 100
    bot = int(min(act + [0]) // 100) * 100
    return ["## 시간별 발전량 (kWh)", "", "🔵 기대 발전량 · 🔴 실측(정지)", "", "```mermaid",
            _init(_XY_COLORS), "xychart-beta", '    title "시간별 발전량 (kWh)"',
            f"    x-axis [{xs}]", f'    y-axis "kWh" {bot} --> {top}',
            f"    line [{', '.join(str(v) for v in exp)}]", f"    line [{', '.join(str(v) for v in act)}]", "```"]


def _mermaid_powercurve(bins):
    xs = ", ".join(f'"{b["w"]}"' for b in bins)
    exp = [_num1(b.get("expected")) for b in bins]
    act = [_num1(b.get("actual")) for b in bins]
    top = (int(max(exp + act + [0]) // 100) + 1) * 100
    return ["## 파워커브 — 🔵 기대 vs 🔴 실측 (풍속 구간별)", "", "```mermaid", _init(_XY_COLORS),
            "xychart-beta", '    title "풍속대별 발전량 (kWh)"', f"    x-axis [{xs}]",
            f'    y-axis "kWh" 0 --> {top}', f"    line [{', '.join(str(v) for v in exp)}]",
            f"    line [{', '.join(str(v) for v in act)}]", "```",
            "모든 풍속대에서 실측이 기대선 아래 → 지속적 성능 저하의 근거."]


def _mermaid_deficit(pct):
    return ["## 단지 대비 뒤처짐", "", "```mermaid", _init(_XY_COLOR_ONE), "xychart-beta",
            '    title "단지 중앙값 대비 뒤처짐 (%p)"', '    x-axis ["뒤처짐"]', '    y-axis "%p" 0 --> 100',
            f"    bar [{round(abs(pct), 1)}]", "```"]


def _mermaid_presence(presence):
    xs = ", ".join(f'"{p["turbine"]}"' for p in presence)
    rows = [p.get("rows", 0) for p in presence]
    down = sum(1 for r in rows if r == 0)
    top = max(rows + [1])
    return ["## 호기별 관측 상태 (부재 창)", "", f"관측 행 수 · 0 = 부재 · **{down}/{len(rows)} 호기 부재**", "",
            "```mermaid", _init(_XY_COLOR_ONE), "xychart-beta", '    title "호기별 관측 행 수 (0 = 부재)"',
            f"    x-axis [{xs}]", f'    y-axis "행" 0 --> {top}', f"    bar [{', '.join(str(r) for r in rows)}]", "```"]


def _weather_table(w):
    if not w or not w.get("found"):
        return []
    return ["## 관측 조건 (시작 시점)", "", "| 기온 | 습도 | 강수 | 기압 | 풍향 |", "|--:|--:|--:|--:|--:|",
            f"| {_f(w.get('temperature'), 1)}°C | {_f(w.get('humidity'), 1)}% | {_f(w.get('precipitation'), 1)}mm "
            f"| {_f(w.get('pressure'), 1)}hPa | {_f(w.get('wind_direction'), 1)}° |", ""]


def _history_section(recent):
    """최근 N개월 동일 유형 발생 내역(코드 렌더 — 날짜·등급·손실은 DB 값)."""
    lines = [f"## 최근 {RECENT_HISTORY_MONTHS}개월 동일 유형 이력", ""]
    if not recent:
        return lines + [f"최근 {RECENT_HISTORY_MONTHS}개월 내 동일 유형 발생 없음.", ""]
    lines += ["| 발생일 | 등급 | 놓친 발전량 |", "|:--|:--:|--:|"]
    for r in recent:
        day = str(r.get("start_time") or "")[:10]
        loss = r.get("estimated_loss_kwh")
        loss_txt = f"{_f(loss, 0)} kWh" if isinstance(loss, (int, float)) else "—"
        lines.append(f"| {day} | {r.get('tier') or '—'} | {loss_txt} |")
    return lines + [""]


def _action_block(et):
    action = ACTION_BY_TYPE.get(et, "담당자 확인 필요.")
    head, sep, rest = action.partition(". ")
    lines = ["## 권고 조치", "", f"{SEVERITY.get(et, '⚪')} **{head}{'.' if sep else ''}**"]
    if rest:
        lines.append(f"> {rest}")
    lines.append("")
    return lines


def render_report(to, analysis: str = None) -> str:
    """표 + 차트 + (종합분석) + 권고 조치 + 판단 유보 조립."""
    e = to["event"]
    et = e.get("event_type")
    sc = to.get("scada", {}) or {}
    w = to.get("weather", {}) or {}
    cnt = to.get("recent_count", {}).get("count", 0)
    when = str(e.get("start_time") or "")[:16]
    status = "진행 중" if e.get("ongoing") else "종료"

    L = [f"# 이상 감지 리포트 — {e.get('turbine_code')}", "",
         f"`{EVENT_TYPE_KO.get(et, et)}` · {status} · 발생 {when}", ""]

    if et == "prolonged_stop":
        L += ["## 핵심 지표", "", "| 지표 | 값 |", "|:--|--:|",
              f"| **놓친 발전량** | **{_f(e.get('estimated_loss_kwh'), 0)} kWh** |",
              f"| 기대 발전량 (누적) | {_f(sc.get('sum_expected_power_pooled'), 0)} kWh |",
              f"| 실측 발전량 (누적) | {_f(sc.get('sum_actual_power'), 0)} kWh |",
              f"| 정지 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'), 1)} m/s |",
              f"| 최근 {RECENT_HISTORY_MONTHS}개월 동일 유형 | {cnt}건 |", ""]
        series = to.get("series") or []
        if series:
            L += _mermaid_profile(series)
            L += [f"두 선의 간격 = 놓친 발전량 **{_f(e.get('estimated_loss_kwh'), 0)} kWh**", ""]

    elif et in ("chronic_screening", "chronic_confirmed"):
        er = e.get("energy_ratio_30d")
        pct = abs(er * 100) if er is not None else None
        grade = "경고 (확정 아님 · 추이 관찰)" if et == "chronic_screening" else "확정"
        L += ["## 핵심 지표", "", "| 지표 | 값 |", "|:--|--:|",
              f"| 단지 대비 뒤처짐 | {_f(pct, 1)}%p |", f"| 등급 | {grade} |",
              f"| 놓친 발전량 | {_f(e.get('estimated_loss_kwh'), 0)} kWh |",
              f"| 관측 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'), 1)} m/s |",
              f"| 최근 {RECENT_HISTORY_MONTHS}개월 동일 유형 | {cnt}건 |", ""]
        pc = to.get("powercurve") or []
        if pc:
            L += _mermaid_powercurve(pc) + [""]
        elif pct is not None:
            L += _mermaid_deficit(pct) + [""]

    elif et == "data_missing":
        scope = e.get("scope")
        scope_txt = ("전 호기 동시 (scope=farm) → 수집·통신 장애 정황" if scope == "farm"
                     else "해당 호기만 (scope=turbine) → 설비 문제 정황" if scope == "turbine" else "범위 미상")
        L += ["## 상태", "", "| 항목 | 값 |", "|:--|:--|", f"| 부재 범위 | {scope_txt} |",
              "| 발전량·손실 | 관측 부재로 산출 불가 |", f"| 최근 동일 유형 이벤트 | {cnt}건 |", ""]
        pr = to.get("presence") or []
        if pr:
            L += _mermaid_presence(pr) + [""]

    L += _weather_table(w)
    L += _history_section(to.get("recent_events") or [])
    if analysis and analysis.strip():
        L += ["## 종합분석", "", analysis.strip(), ""]
    L += _action_block(et)
    L += ["## 판단 유보", "", DISCLAIMER, ""]
    return "\n".join(L) + "\n"


# ── LLM 종합분석용 facts (수치 인용 소스) — defect·원본 anomaly처럼 builder 안에 둔다 ──
# 표·차트와 같은 파일에서 수치를 만들어, 서술이 인용하는 값과 표의 값이 어긋나지 않게 한다.
STYLE = {
    "prolonged_stop": "",
    "data_missing": "'데이터 부재'는 터빈이 멈췄다는 뜻이 아니라 현재 상태를 알 수 없게 되었다는 뜻임을 설명하라. "
                    "발전 손실량은 산출 불가임을 명시하라.",
    "chronic": "'달성률'(받은 바람으로 낼 수 있었던 발전량 중 실제로 낸 비율) 개념을 쉽게 풀어 설명하고, "
               "계절·바람 세기는 전 호기에 공통이라 비교에서 이미 제외됨을 언급하라. "
               "자연 변동 폭 초과·우연 발생 확률로 등급 판정 근거를 서술하라.",
}
_FACT_WHITELIST = {6.0, 24.0, float(CHRONIC_DAYS), 720.0}   # 유형 임계값(데이터 값 아님)


def _fmt_dt(s):
    ts = pd.to_datetime(s)
    return f"{ts.year}년 {ts.month}월 {ts.day}일 {ts.hour:02d}시"


def _weather_fact(w):
    if not w or not w.get("found"):
        return []
    return [f"- 시작 시점 기온: {_f(w['temperature'], 1)} ℃",
            f"- 시작 시점 기압: {_f(w['pressure'], 1)} hPa",
            f"- 시작 시점 습도: {_f(w['humidity'], 1)} %",
            f"- 시작 시점 강수량: {_f(w['precipitation'], 1)} mm"]


def _facts_prolonged(to):
    e, sc, oth = to["event"], to.get("scada", {}) or {}, to.get("others", {}) or {}
    lines = [f"- 대상 호기: {e['turbine_code']}", f"- 발생 시각: {_fmt_dt(e['start_time'])}",
             "- 정지 기준: 24시간 이상 연속"]
    if sc.get("found"):
        lines += [f"- 정지 기간 평균 풍속: {_f(sc['avg_wind_speed'], 1)} m/s",
                  f"- 정지 기간 최대 풍속: {_f(sc['max_wind_speed'], 1)} m/s"]
    if oth.get("n"):
        lines += [f"- 동시 운전 중이던 타 호기 수: {oth['n']}개",
                  f"- 타 호기 발전량 범위: {_f(oth['lo'], 0)} ~ {_f(oth['hi'], 0)} kW"]
    lines.append("- 해당 호기 발전량: 0 (대기전력 소비 상태)")
    lines += _weather_fact(to.get("weather", {}))
    if e.get("estimated_loss_kwh") is not None:
        lines.append(f"- 추정 미생산 전력량: {_f(e['estimated_loss_kwh'], 0)} kWh")
    return lines


def _facts_data_missing(to):
    e, pr = to["event"], to.get("presence") or []
    total, down = len(pr), sum(1 for p in pr if p.get("rows", 0) == 0)
    if e.get("scope") == "farm":
        scope_txt = f"전 호기 동시 부재 (단지 {total}개 호기 중 {down}개 동시 수신 중단, scope=farm)"
    else:
        scope_txt = "해당 호기 단독 부재 (scope=turbine)"
    return [f"- 대상 호기: {e['turbine_code']}", f"- 수신 중단 시각: {_fmt_dt(e['start_time'])}",
            "- 부재 기준: 6시간 이상 연속 데이터 미수신", f"- 부재 범위: {scope_txt}",
            "- 발전 손실량: 산출 불가 (관측 자체가 부재)"] + _weather_fact(to.get("weather", {}))


def _facts_chronic(to, confirmed):
    e = to["event"]
    pct = abs((e.get("energy_ratio_30d") or 0) * 100)
    lines = [f"- 대상 호기: {e['turbine_code']}", f"- 분석 기간: 최근 {CHRONIC_DAYS}일",
             f"- 달성률 단지 평균 대비 뒤처짐: {_f(pct, 1)}%p",
             f"- 정상 호기 간 자연 변동 폭: {_f(CHRONIC_NATURAL_VAR_PP, 1)}%p",
             f"- 확정 등급 전환 임계: {_f(CHRONIC_CONFIRM_THRESHOLD_PP, 1)}%p",
             f"- 정상 상태에서 우연 발생 확률: {'1% 미만' if confirmed else '5% 이내'}",
             f"- 판정 등급: {'확정' if confirmed else '경고'}"]
    if e.get("estimated_loss_kwh") is not None:
        lines.append(f"- 최근 {CHRONIC_DAYS}일 추정 미생산 전력량: {_f(e['estimated_loss_kwh'], 0)} kWh")
    return lines


_FACTS = {
    "prolonged_stop": _facts_prolonged,
    "data_missing": _facts_data_missing,
    "chronic_confirmed": lambda to: _facts_chronic(to, True),
    "chronic_screening": lambda to: _facts_chronic(to, False),
}


def fact_lines(to) -> list:
    """LLM 종합분석용 팩트 블록 = 유형별 집계 + 최근 이력 요약. LLM은 이 수치만 인용 가능."""
    et = to["event"].get("event_type")
    lines = list(_FACTS[et](to))
    rec = to.get("recent_events") or []
    if rec:
        lines.append(f"- 최근 {RECENT_HISTORY_MONTHS}개월 동일 유형 발생 건수: {len(rec)}건")
    return lines


def allowed_numbers(to) -> set:
    """분석이 인용해도 되는 수치(절대값) 집합 — fact_lines에서 파생 + 유형 임계값."""
    return {abs(n) for n in extract_numbers("\n".join(fact_lines(to)))} | _FACT_WHITELIST


def style_hint(to) -> str:
    et = to["event"].get("event_type") or ""
    return STYLE.get("chronic" if et.startswith("chronic") else et, "")
