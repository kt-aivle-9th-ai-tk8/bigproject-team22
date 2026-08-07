"""결정론 렌더러 (LLM 없음) — 표 + 유니코드/mermaid 차트 + 권고 조치 + 판단 유보.

'## 종합분석' 섹션에는 LLM이 생성한 서술(수치 grounding 통과)이 들어간다.
모든 표·차트 수치는 코드가 tool_outputs에서 주입(단일 출처).
"""
import pandas as pd

from app.core.config import (
    RECENT_HISTORY_MONTHS, CHRONIC_NATURAL_VAR_PP, CHRONIC_CONFIRM_THRESHOLD_PP, CHRONIC_DAYS,
    CHRONIC_NEAR_TOTAL_PP,
)
from app.agents.verify import extract_numbers

EVENT_TYPE_KO = {
    "prolonged_stop": "발전 정지(24시간 이상 연속)",
    "data_missing": "데이터 부재(6시간 이상 연속)",
    "chronic_screening": "만성 성능저하 경고",
    "chronic_confirmed": "만성 성능저하 확정",
}

# 확인 순서 머리 문장(누가/얼마나 급한지). 자연스러운 서술문으로 둔다(대시·기호 없이).
ACTION_BY_TYPE = {
    "prolonged_stop": "발전 손실이 진행 중이므로 운영팀이 즉시 확인해야 합니다.",
    "data_missing": "관측 자체가 불가한 상태이므로 시스템팀 확인이 필요합니다.",
    "chronic_confirmed": "성능 저하가 확정되어 드론 출동으로 외관과 현장을 확인해야 합니다.",
    "chronic_screening": "성능 저하가 의심되므로 드론 검토와 추이 관찰이 필요합니다.",
}

SEVERITY = {"prolonged_stop": "🔴", "chronic_confirmed": "🟠", "chronic_screening": "🟡", "data_missing": "🔵"}

# 확인 순서(체크리스트) — 판단은 사람이 하되 '순서'만 제공(원인·조치 단정 아님).
# data_missing 은 scope 별로 확인 경로가 달라 분리한다(전 호기=인프라 / 단독=설비).
CHECKLIST = {
    "prolonged_stop": [
        "출력제어(계통 지시) 여부 확인",
        "SCADA 알람·상태코드 확인",
        "현장 점검 (필요 시 출동)",
    ],
    "chronic_confirmed": [
        "같은 기간에 정지가 함께 있었는지 확인",
        "드론 출동해 블레이드 외관 점검",
        "정비 이력·파워커브 추이 대조",
    ],
    "chronic_screening": [
        "다음 관측 창에서 추이 재평가",
        "드론 검토 필요성 판단",
        "정비 이력 대조",
    ],
}
CHECKLIST_DATA_MISSING = {
    "farm": [
        "수집 서버·게이트웨이(통신망) 상태 확인",
        "동시 부재 호기 범위 확인",
        "통신사/네트워크 장애 여부 확인",
    ],
    "turbine": [
        "해당 호기 통신 모듈·센서 상태 확인",
        "SCADA 연결·구성 확인",
        "현장 점검 (필요 시 출동)",
    ],
}

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
    return ["## 파워커브 · 🔵 기대 vs 🔴 실측 (풍속 구간별)", "", "```mermaid", _init(_XY_COLORS),
            "xychart-beta", '    title "풍속대별 발전량 (kWh)"', f"    x-axis [{xs}]",
            f'    y-axis "kWh" 0 --> {top}', f"    line [{', '.join(str(v) for v in exp)}]",
            f"    line [{', '.join(str(v) for v in act)}]", "```",
            "모든 풍속 구간에서 실제 발전량(🔴)이 기대치(🔵)보다 낮아, 성능이 지속적으로 떨어지고 있음을 보여줍니다."]


def _mermaid_deficit(pct):
    return ["## 단지 대비 뒤처짐", "", "```mermaid", _init(_XY_COLOR_ONE), "xychart-beta",
            '    title "단지 중앙값 대비 뒤처짐 (%p)"', '    x-axis ["뒤처짐"]', '    y-axis "%p" 0 --> 100',
            f"    bar [{round(abs(pct), 1)}]", "```"]


def _mermaid_presence(presence):
    xs = ", ".join(f'"{p["turbine"]}"' for p in presence)
    rows = [p.get("rows", 0) for p in presence]
    down = sum(1 for r in rows if r == 0)
    top = max(rows + [1])
    return ["## 호기별 데이터 수신 상태", "", f"수신 건수 · 0 = 미수신 · **{down}/{len(rows)} 호기 미수신**", "",
            "```mermaid", _init(_XY_COLOR_ONE), "xychart-beta", '    title "호기별 데이터 수신 건수 (0 = 미수신)"',
            f"    x-axis [{xs}]", f'    y-axis "건수" 0 --> {top}', f"    bar [{', '.join(str(r) for r in rows)}]", "```"]


def _snow_cell(v):
    return f"{_f(v, 1)}cm" if isinstance(v, (int, float)) and not isinstance(v, bool) else "—"


def _weather_table(w):
    """기온·습도·강수·기압·풍향 표(섹션 헤더 없이 표만)."""
    return ["| 기온 | 습도 | 강수 | 기압 | 풍향 |", "|--:|--:|--:|--:|--:|",
            f"| {_f(w.get('temperature'), 1)}°C | {_f(w.get('humidity'), 1)}% | {_f(w.get('precipitation'), 1)}mm "
            f"| {_f(w.get('pressure'), 1)}hPa | {_f(w.get('wind_direction'), 1)}° |"]


def _snow_table(w):
    """적설(3시간 신적설·일 신적설·적설) 표만. 세 값 모두 결측(비강설기)이면 []."""
    vals = (w.get("snow_hr3"), w.get("snow_day"), w.get("snow_tot"))
    if all(v is None for v in vals):
        return []
    return ["| 3시간 신적설 | 일 신적설 | 적설 |", "|--:|--:|--:|",
            f"| {_snow_cell(vals[0])} | {_snow_cell(vals[1])} | {_snow_cell(vals[2])} |"]


def _weather_section(w, when_str):
    """## 날씨 — '언제 관측된 날씨인지' 문장으로 안내한 뒤 표(+적설)를 붙인다."""
    if not w or not w.get("found"):
        return []
    lines = ["## 날씨", "", f"이상이 관측된 {when_str}의 날씨입니다.", ""]
    lines += _weather_table(w) + [""]
    snow = _snow_table(w)
    if snow:
        lines += ["**적설**", ""] + snow + [""]
    return lines


def _history_section(recent):
    """최근 N개월 동일 유형 발생 내역(코드 렌더 — 날짜·등급·손실은 DB 값)."""
    lines = [f"## 해당 호기의 최근 {RECENT_HISTORY_MONTHS}개월 같은 이상 이력", ""]
    if not recent:
        return lines + [f"해당 호기에서 최근 {RECENT_HISTORY_MONTHS}개월 내 같은 이상은 없었습니다.", ""]
    lines += ["| 발생일 | 등급 | 놓친 발전량 |", "|:--|:--:|--:|"]
    for r in recent:
        day = str(r.get("start_time") or "")[:10]
        loss = r.get("estimated_loss_kwh")
        loss_txt = f"{_f(loss, 0)} kWh" if isinstance(loss, (int, float)) else "—"
        lines.append(f"| {day} | {r.get('tier') or '—'} | {loss_txt} |")
    return lines + [""]


def _checklist_block(et, scope=None):
    """확인 순서(체크리스트) — 유형별(부재는 scope별) 정형 순서. 판단이 아니라 순서 안내.

    머리에 '누가/얼마나 급한지'(구 권고 조치)를 흡수한다. 세부 확인 항목은 아래 순서로 대체.
    """
    if et == "data_missing":
        items = CHECKLIST_DATA_MISSING.get(scope)
    else:
        items = CHECKLIST.get(et)
    if not items:
        return []
    head = ACTION_BY_TYPE.get(et, "담당자 확인이 필요합니다.")
    lines = ["## 확인 순서", "",
             f"{head} 판단·조치는 담당자가 하되, 아래 순서로 확인을 권장합니다.", ""]
    lines += [f"{i}. {step}" for i, step in enumerate(items, 1)]
    return lines + [""]


def _summary_line(to):
    """맨 앞 한 줄 요약 — 담당자가 표 전에 무슨 일인지 바로 알게. 코드 용어 없이 평문."""
    e = to["event"]
    et = e.get("event_type")
    code = e.get("turbine_code") or "해당"
    if et == "prolonged_stop":
        return f"{code} 호기가 24시간 연속 발전을 멈춘 상태입니다."
    if et == "data_missing":
        if e.get("scope") == "farm":
            return ("단지 전체 호기에서 데이터가 6시간 연속 들어오지 않고 있습니다. "
                    "수집·통신 장애일 가능성이 있습니다.")
        return f"{code} 호기에서 데이터가 6시간 연속 들어오지 않고 있습니다."
    if et == "chronic_confirmed":
        return f"{code} 호기의 최근 30일 발전 성능이 단지 평균보다 크게 뒤처져 점검이 필요합니다."
    if et == "chronic_screening":
        return f"{code} 호기의 최근 30일 발전 성능이 단지 평균보다 뒤처져 주의가 필요합니다."
    return "관측된 이상입니다."


def render_report(to, analysis: str = None) -> str:
    """표 + 차트 + (종합분석) + 확인 순서 + 판단 유보 조립."""
    e = to["event"]
    et = e.get("event_type")
    sc = to.get("scada", {}) or {}
    w = to.get("weather", {}) or {}
    cnt = to.get("recent_count", {}).get("count", 0)

    farm = e.get("wind_farm_name") or ""
    day = str(e.get("start_time") or "")[:10]
    title = f"{farm} {e.get('turbine_code')}터빈 {day} 이상감지 보고서".strip()
    L = [f"# {title}", "",
         f"{_fmt_dt(e.get('start_time'))}에 관측된 이상입니다.", "",
         _summary_line(to), ""]

    if et == "prolonged_stop":
        L += ["## 핵심 지표", "", "| 지표 | 값 |", "|:--|--:|",
              f"| **놓친 발전량** | **{_f(e.get('estimated_loss_kwh'), 0)} kWh** |",
              f"| 기대 발전량 (24시간 누적) | {_f(sc.get('sum_expected_power_pooled'), 0)} kWh |",
              f"| 실측 발전량 (24시간 누적) | {_f(sc.get('sum_actual_power'), 0)} kWh |",
              f"| 정지 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'), 1)} m/s |",
              f"| 해당 호기 최근 {RECENT_HISTORY_MONTHS}개월 같은 사례 | {cnt}건 |", "",
              "놓친 발전량은 시스템이 산출한 값으로, 위 24시간 누적 기대·실측의 단순 차와는 "
              "계산 방식이 달라 정확히 일치하지 않을 수 있습니다.", ""]
        series = to.get("series") or []
        if series:
            L += _mermaid_profile(series)
            L += [f"두 선의 간격 = 놓친 발전량 **{_f(e.get('estimated_loss_kwh'), 0)} kWh**", ""]

    elif et in ("chronic_screening", "chronic_confirmed"):
        er = e.get("energy_ratio_30d")
        pct = abs(er * 100) if er is not None else None
        grade = ("경고 (성능 저하 의심 · 추이 관찰)" if et == "chronic_screening"
                 else "확정 (성능 저하 뚜렷 · 점검 권장)")
        L += ["## 핵심 지표", "", "| 지표 | 값 |", "|:--|--:|",
              f"| 단지 대비 뒤처짐 | {_f(pct, 1)}%p |", f"| 등급 | {grade} |",
              f"| 놓친 발전량 | {_f(e.get('estimated_loss_kwh'), 0)} kWh |",
              f"| 관측 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'), 1)} m/s |",
              f"| 해당 호기 최근 {RECENT_HISTORY_MONTHS}개월 같은 사례 | {cnt}건 |", "",
              "'경고'는 성능 저하가 의심되어 지켜보는 단계이고, '확정'은 저하가 뚜렷해 우연으로 보기 어려운 단계입니다.", "",
              "여기 '놓친 발전량'은 터빈이 돌고 있는 동안 덜 낸 양만 더한 값입니다. "
              "아예 멈춰 있던 시간은 빠지며, 그 부분은 정지 보고서에서 따로 다룹니다.", ""]
        # 1b: 사실상 장기 미가동 — 관측된 뒤처짐이 임계 이상이면 담당자용 안내 문장.
        if pct is not None and pct >= CHRONIC_NEAR_TOTAL_PP:
            L += [f"단지 평균보다 {_f(pct, 1)}%p나 뒤처진 것은 발전하던 중 성능이 떨어졌다기보다, "
                  "관측 기간 대부분을 멈춰 있었던 상태에 가깝습니다. 해당 호기의 정지 이력을 함께 확인하시기 바랍니다.", ""]
        pc = to.get("powercurve") or []
        if pc:
            L += _mermaid_powercurve(pc) + [""]
        elif pct is not None:
            L += _mermaid_deficit(pct) + [""]

    elif et == "data_missing":
        # 범위(단지 전체/개별)는 요약 문장과 아래 수신 상태 차트로 이미 드러나므로 표에서는 생략.
        L += ["## 상태", "", "| 항목 | 값 |", "|:--|:--|",
              "| 데이터 미수신 | 6시간 연속 |",
              "| 발전량·손실 | 데이터가 없어 산출 불가 |",
              f"| 해당 호기 최근 {RECENT_HISTORY_MONTHS}개월 같은 사례 | {cnt}건 |", ""]
        pr = to.get("presence") or []   # 단지 동시 부재일 때만 채워짐 — 전 호기 동시 미수신의 직접 증거
        if pr:
            L += _mermaid_presence(pr) + [""]

    L += _weather_section(w, _fmt_dt(e.get("start_time")))
    L += _history_section(to.get("recent_events") or [])
    if analysis and analysis.strip():
        L += ["## 종합분석", "", analysis.strip(), ""]
    L += _checklist_block(et, e.get("scope"))
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
               "등급(경고/확정)이 담당자에게 무엇을 뜻하는지 쉬운 말로 전하되(확정은 우연으로 보기 어려워 점검이 필요한 단계), "
               "유의수준·표준편차·z값 같은 통계 용어는 쓰지 마라.",
}
_FACT_WHITELIST = {6.0, 24.0, float(CHRONIC_DAYS), 720.0}   # 유형 임계값(데이터 값 아님)


def _fmt_dt(s):
    ts = pd.to_datetime(s)
    return f"{ts.year}년 {ts.month}월 {ts.day}일 {ts.hour:02d}시"


def _weather_fact(w):
    if not w or not w.get("found"):
        return []
    lines = [f"- 시작 시점 기온: {_f(w['temperature'], 1)} ℃",
             f"- 시작 시점 기압: {_f(w['pressure'], 1)} hPa",
             f"- 시작 시점 습도: {_f(w['humidity'], 1)} %",
             f"- 시작 시점 강수량: {_f(w['precipitation'], 1)} mm"]
    for label, key in (("3시간 신적설", "snow_hr3"), ("일 신적설", "snow_day"), ("적설", "snow_tot")):
        v = w.get(key)
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            lines.append(f"- 시작 시점 {label}: {_f(v, 1)} cm")
    return lines


def _facts_prolonged(to):
    e, sc, oth = to["event"], to.get("scada", {}) or {}, to.get("others", {}) or {}
    lines = [f"- 대상 호기: {e['turbine_code']}", f"- 발생 시각: {_fmt_dt(e['start_time'])}",
             "- 정지 지속: 24시간 연속(자동 감지 시점)"]
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
    if e.get("scope") == "farm":
        total, down = len(pr), sum(1 for p in pr if p.get("rows", 0) == 0)
        scope_txt = f"단지 전 호기 동시 미수신 (단지 {total}개 호기 중 {down}개 동시 중단)"
    else:
        # scope 판정은 탐지기 소유. 다른 호기 상태는 단정하지 않는다(동시 정지 가능 — 각자 개별 이벤트).
        scope_txt = "해당 호기 데이터 미수신"
    return [f"- 대상 호기: {e['turbine_code']}", f"- 수신 중단 시각: {_fmt_dt(e['start_time'])}",
            "- 미수신 지속: 6시간 연속(자동 감지 시점)", f"- 미수신 범위: {scope_txt}",
            "- 발전 손실량: 산출 불가 (데이터가 없어 계산 불가)"] + _weather_fact(to.get("weather", {}))


def _facts_chronic(to, confirmed):
    e = to["event"]
    er = e.get("energy_ratio_30d")
    pct = abs(er * 100) if er is not None else None
    lines = [f"- 대상 호기: {e['turbine_code']}", f"- 분석 기간: 최근 {CHRONIC_DAYS}일",
             f"- 달성률 단지 평균 대비 뒤처짐: {_f(pct, 1)}%p",
             f"- 정상이라면 이만큼 뒤처질 우연 확률: {'1% 미만' if confirmed else '5% 이내'}",
             f"- 판정 등급: {'확정(성능 저하 뚜렷 · 점검 권장)' if confirmed else '경고(성능 저하 의심 · 추이 관찰)'}"]
    if e.get("estimated_loss_kwh") is not None:
        lines.append(f"- 최근 {CHRONIC_DAYS}일 추정 미생산 전력량(발전하던 중 덜 낸 몫만): {_f(e['estimated_loss_kwh'], 0)} kWh")
        lines.append("- 놓친 발전량 집계 범위: 발전하던 중 덜 낸 몫만 (멈춰 있던 시간·데이터 없는 시간은 제외, 정지는 별도 집계)")
    if pct is not None and pct >= CHRONIC_NEAR_TOTAL_PP:
        lines.append("- 관측 소견: 뒤처짐이 매우 커서, 발전 중 성능 저하가 아니라 대부분 시간을 멈춰 있었던 상태에 가까움 (장기 정지 동반 가능)")
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
