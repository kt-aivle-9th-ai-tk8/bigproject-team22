"""anomaly 결정론적 렌더러 (LLM 없음). 관측 사실만 시각화 → 사람이 판단(판단 유보 원칙).

render_report는 표 + 유니코드 차트로 .md 리포트를 조립한다. LLM 분석 섹션은 없다
(환각 위험 구조적 0). 모든 수치는 코드가 tool_outputs에서 주입한 사실이다.

build_detail/fact_lines/allowed_numbers는 LLM 분석을 검증하던 critic 계약용으로 남겨둔다
(anomaly는 분석을 안 쓰지만, narrative를 쓰는 다른 report_type이 재사용할 수 있는 스캐폴드).
"""
from app.agents.verify import extract_numbers

EVENT_TYPE_KO = {
    "prolonged_stop": "발전 정지(24시간 이상 연속)",
    "data_missing": "데이터 부재(6시간 이상 연속)",
    "chronic_screening": "만성 성능저하 경고",
    "chronic_confirmed": "만성 성능저하 확정",
}

# event_type → 표준 권고 조치 (파이프라인 문서 ⑦ 알림 라우팅). 코드가 결정, LLM 아님.
#   prolonged_stop 주의: 탐지 플래그(flag_highwind_neg="바람 충분한데 출력 음수")가
#   고장 정지와 출력제어(계통 지시)를 구분하지 않는다(pipeline ③). 그래서 '고장' 단정 대신
#   출력제어 여부부터 배제하도록 권고한다. '가능성이 높다'는 근거(base rate) 없어 쓰지 않음.
ACTION_BY_TYPE = {
    "prolonged_stop": (
        "운영팀 즉시 확인 — 발전 손실 진행 중. "
        "출력제어(계통 지시) 여부부터 확인 — 데이터상 고장 정지와 출력제어가 구분되지 않음."
    ),
    "data_missing": "시스템팀 확인 — 관측 자체가 불가한 상태.",
    "chronic_confirmed": "드론 출동 권고 — 성능 저하 확정, 외관·현장 확인.",
    "chronic_screening": "드론 검토 — 성능 저하 의심, 추이 관찰.",
}

DISCLAIMER = (
    "본 보고서는 관측 사실과 정황만 제시합니다. 원인 판단·조치 결정은 담당자 확인이 필요합니다."
)

# event_type → 심각도 표식 (권고 조치 앞). 라우팅과 같은 축: 운영 즉시=적색.
SEVERITY = {
    "prolonged_stop": "🔴",
    "chronic_confirmed": "🟠",
    "chronic_screening": "🟡",
    "data_missing": "🔵",
}

def _f(v, nd=2):
    """숫자 → 고정 소수 문자열, None/비수치 → '—'."""
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return "—"
    return f"{v:,.{nd}f}"


def _num1(v):
    """mermaid 데이터용: None → 0, 그 외 소수1자리 반올림."""
    return round(v, 1) if isinstance(v, (int, float)) and not isinstance(v, bool) else 0


# mermaid xychart 기본 팔레트가 노랑/주황이라 저대비 → 선 색을 명시(파랑=기대, 빨강=실측/심각).
_XY_COLORS = "#3b82f6, #ef4444"      # 라인 2개: 파랑, 빨강
_XY_COLOR_ONE = "#ef4444"            # 막대 1개: 빨강


def _init(palette) -> str:
    return f'%%{{init: {{"themeVariables": {{"xyChart": {{"plotColorPalette": "{palette}"}}}}}}}}%%'


def _mermaid_profile(series) -> list:
    """기대 vs 실측 시간별 발전량 → mermaid xychart-beta 라인차트(fenced block)."""
    xs = ", ".join(f'"{str(p.get("t"))[:2]}"' for p in series)  # 시(hour)만
    exp = [_num1(p.get("expected")) for p in series]
    act = [_num1(p.get("actual")) for p in series]
    top = (int(max(exp + [0]) // 100) + 1) * 100
    bot = int(min(act + [0]) // 100) * 100
    return [
        "## 시간별 발전량 (kWh)",
        "",
        "🔵 기대 발전량 · 🔴 실측(정지)",
        "",
        "```mermaid",
        _init(_XY_COLORS),
        "xychart-beta",
        '    title "시간별 발전량 (kWh)"',
        f"    x-axis [{xs}]",
        f'    y-axis "kWh" {bot} --> {top}',
        f"    line [{', '.join(str(v) for v in exp)}]",
        f"    line [{', '.join(str(v) for v in act)}]",
        "```",
    ]


def _mermaid_deficit(pct) -> list:
    """단지 대비 뒤처짐(%p) → mermaid xychart-beta 막대(0~100 고정축, 심각도 비교)."""
    return [
        "## 단지 대비 뒤처짐",
        "",
        "```mermaid",
        _init(_XY_COLOR_ONE),
        "xychart-beta",
        '    title "단지 중앙값 대비 뒤처짐 (%p)"',
        '    x-axis ["뒤처짐"]',
        '    y-axis "%p" 0 --> 100',
        f"    bar [{round(abs(pct), 1)}]",
        "```",
    ]


def _mermaid_powercurve(bins) -> list:
    """만성 성능저하: 풍속 구간별 기대 vs 실측 → mermaid 라인차트(파워커브)."""
    xs = ", ".join(f'"{b["w"]}"' for b in bins)
    exp = [_num1(b.get("expected")) for b in bins]
    act = [_num1(b.get("actual")) for b in bins]
    top = (int(max(exp + act + [0]) // 100) + 1) * 100
    return [
        "## 파워커브 — 🔵 기대 vs 🔴 실측 (풍속 구간별)",
        "",
        "```mermaid",
        _init(_XY_COLORS),
        "xychart-beta",
        '    title "풍속대별 발전량 (kWh)"',
        f"    x-axis [{xs}]",
        f'    y-axis "kWh" 0 --> {top}',
        f"    line [{', '.join(str(v) for v in exp)}]",
        f"    line [{', '.join(str(v) for v in act)}]",
        "```",
        "모든 풍속대에서 실측이 기대선 아래 → 지속적 성능 저하의 근거.",
    ]


def _mermaid_presence(presence) -> list:
    """data_missing: 호기별 관측 행 수(0=부재) → mermaid 막대. scope를 그림으로."""
    xs = ", ".join(f'"{p["turbine"]}"' for p in presence)
    rows = [p.get("rows", 0) for p in presence]
    down = sum(1 for r in rows if r == 0)
    top = max(rows + [1])
    return [
        "## 호기별 관측 상태 (부재 창)",
        "",
        f"관측 행 수 · 0 = 부재 · **{down}/{len(rows)} 호기 부재**",
        "",
        "```mermaid",
        _init(_XY_COLOR_ONE),
        "xychart-beta",
        '    title "호기별 관측 행 수 (0 = 부재)"',
        f"    x-axis [{xs}]",
        f'    y-axis "행" 0 --> {top}',
        f"    bar [{', '.join(str(r) for r in rows)}]",
        "```",
    ]


def _weather_line(w):
    if not w or not w.get("found"):
        return None
    return (
        f"- 시작 시점 날씨: 기온 {_f(w.get('temperature'), 1)}°C, "
        f"습도 {_f(w.get('humidity'), 1)}%, 강수 {_f(w.get('precipitation'), 1)}mm, "
        f"기압 {_f(w.get('pressure'), 1)}hPa, 풍향 {_f(w.get('wind_direction'), 1)}°"
    )


def build_detail(to) -> list:
    """event_type별 '상세' 라인들을 코드로 생성 (숫자 전부 코드 주입)."""
    e = to["event"]
    sc = to.get("scada", {}) or {}
    w = to.get("weather", {}) or {}
    et = e.get("event_type")
    lines = []

    if et == "prolonged_stop":
        if sc.get("found"):
            lines.append(f"- 정지 기간 평균 풍속: {_f(sc.get('avg_wind_speed'))} m/s")
            lines.append(f"- 기대 발전량(누적): {_f(sc.get('sum_expected_power_pooled'), 1)} kWh")
            lines.append(f"- 실측 발전량(누적): {_f(sc.get('sum_actual_power'), 1)} kWh")
            # '정지 시간 비율'은 넣지 않는다: prolonged_stop은 '정지 24h 연속'이 트리거이고
            # 관측창도 그 24h라, 이 비율은 정의상 항상 ~100% → 동어반복(무정보). 재추가 금지.
        if e.get("estimated_loss_kwh") is not None:
            lines.append(f"- 놓친 발전량: {_f(e.get('estimated_loss_kwh'), 1)} kWh")
        wl = _weather_line(w)
        if wl:
            lines.append(wl)

    elif et == "data_missing":
        scope = e.get("scope")
        if scope == "farm":
            lines.append("- 전 호기 동시 부재 → 수집·통신 장애 정황 (scope=farm)")
        elif scope == "turbine":
            lines.append("- 해당 호기만 부재 → 설비 문제 정황 (scope=turbine)")
        lines.append("- 관측 자체가 없어 발전량·손실은 산출하지 않음")
        wl = _weather_line(w)
        if wl:
            lines.append(wl)

    elif et in ("chronic_screening", "chronic_confirmed"):
        er = e.get("energy_ratio_30d")
        if er is not None:
            lines.append(f"- 단지 대비 뒤처짐: {_f(er * 100, 1)}%p")
        grade = "경고 (확정 아님 · 추이 관찰 필요)" if et == "chronic_screening" else "확정"
        lines.append(f"- 등급: {grade}")
        if e.get("estimated_loss_kwh") is not None:
            lines.append(f"- 놓친 발전량: {_f(e.get('estimated_loss_kwh'), 1)} kWh")
        if sc.get("found"):
            lines.append(f"- 관측 기간 평균 풍속: {_f(sc.get('avg_wind_speed'))} m/s")

    return lines


def fact_lines(to) -> list:
    """LLM 분석용 팩트 블록 = 상세(코드 수치) + 최근 이벤트 건수.

    LLM은 이 블록의 수치만 인용할 수 있다(생성 금지). allowed_numbers와 같은 출처.
    """
    lines = list(build_detail(to))
    cnt = to.get("recent_count", {}).get("count", 0)
    lines.append(f"- 해당 터빈 최근 동일 유형 이벤트: {cnt}건")
    return lines


def allowed_numbers(to) -> set:
    """분석이 인용해도 되는 수치(절대값) 집합. 코드가 생성한 팩트·유형명에서만 추출.

    - 상세(코드 수치) + 최근 건수 + 유형명(예: '발전 정지(24시간 이상 연속)'의 24·6 임계값)
    - 부호는 자연어에서 자주 뒤집히므로('8.5%p 뒤처짐' vs 저장값 -8.5) 절대값으로 비교.
    """
    et = to["event"].get("event_type")
    text = "\n".join(fact_lines(to)) + "\n" + EVENT_TYPE_KO.get(et, "")
    return {abs(n) for n in extract_numbers(text)}


def _weather_table(w) -> list:
    if not w or not w.get("found"):
        return []
    return [
        "## 관측 조건 (시작 시점)",
        "",
        "| 기온 | 습도 | 강수 | 기압 | 풍향 |",
        "|--:|--:|--:|--:|--:|",
        f"| {_f(w.get('temperature'), 1)}°C | {_f(w.get('humidity'), 1)}% "
        f"| {_f(w.get('precipitation'), 1)}mm | {_f(w.get('pressure'), 1)}hPa "
        f"| {_f(w.get('wind_direction'), 1)}° |",
        "",
    ]


def _action_block(et) -> list:
    """권고 조치 — 심각도 표식 + 첫 문장 강조, 이후 부연은 인용(>)으로."""
    action = ACTION_BY_TYPE.get(et, "담당자 확인 필요.")
    head, sep, rest = action.partition(". ")  # 첫 문장 / 부연 분리
    lines = ["## 권고 조치", "", f"{SEVERITY.get(et, '⚪')} **{head}{'.' if sep else ''}**"]
    if rest:
        lines.append(f"> {rest}")
    lines.append("")
    return lines


def render_report(to, analysis: str = None) -> str:
    """.md 리포트 조립. event_type별 표 + mermaid 차트 + (선택) 숫자 없는 '분석'.

    analysis가 있으면 '권고 조치' 바로 위에 '## 분석'으로 삽입한다(수치는 표·차트가 소유).
    """
    e = to["event"]
    et = e.get("event_type")
    sc = to.get("scada", {}) or {}
    w = to.get("weather", {}) or {}
    cnt = to.get("recent_count", {}).get("count", 0)
    when = str(e.get("start_time") or "")[:16]  # 초 제거
    status = "진행 중" if e.get("ongoing") else "종료"

    L = [
        f"# 이상 감지 리포트 — {e.get('turbine_code')}",
        "",
        f"`{EVENT_TYPE_KO.get(et, et)}` · {status} · 발생 {when}",
        "",
    ]

    if et == "prolonged_stop":
        L += [
            "## 핵심 지표",
            "",
            "| 지표 | 값 |",
            "|:--|--:|",
            f"| **놓친 발전량** | **{_f(e.get('estimated_loss_kwh'), 1)} kWh** |",
            f"| 기대 발전량 (누적) | {_f(sc.get('sum_expected_power_pooled'), 1)} kWh |",
            f"| 실측 발전량 (누적) | {_f(sc.get('sum_actual_power'), 1)} kWh |",
            f"| 정지 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'))} m/s |",
            f"| 최근 동일 유형 이벤트 | {cnt}건 |",
            "",
        ]
        series = to.get("series") or []
        if series:
            L += _mermaid_profile(series)
            L += [
                f"두 선의 간격 = 놓친 발전량 **{_f(e.get('estimated_loss_kwh'), 1)} kWh**",
                "",
            ]

    elif et in ("chronic_screening", "chronic_confirmed"):
        er = e.get("energy_ratio_30d")
        pct = abs(er * 100) if er is not None else None  # 부호는 '뒤처짐'이 담당
        grade = "경고 (확정 아님 · 추이 관찰)" if et == "chronic_screening" else "확정"
        L += [
            "## 핵심 지표",
            "",
            "| 지표 | 값 |",
            "|:--|--:|",
            f"| 단지 대비 뒤처짐 | {_f(pct, 1)}%p |",
            f"| 등급 | {grade} |",
            f"| 놓친 발전량 | {_f(e.get('estimated_loss_kwh'), 1)} kWh |",
            f"| 관측 기간 평균 풍속 | {_f(sc.get('avg_wind_speed'))} m/s |",
            f"| 최근 동일 유형 이벤트 | {cnt}건 |",
            "",
        ]
        pc = to.get("powercurve") or []
        if pc:
            L += _mermaid_powercurve(pc)
            L += [""]
        elif pct is not None:  # 파워커브 데이터 없을 때 폴백
            L += _mermaid_deficit(pct)
            L += [""]

    elif et == "data_missing":
        scope = e.get("scope")
        scope_txt = (
            "전 호기 동시 (scope=farm) → 수집·통신 장애 정황" if scope == "farm"
            else "해당 호기만 (scope=turbine) → 설비 문제 정황" if scope == "turbine"
            else "범위 미상"
        )
        L += [
            "## 상태",
            "",
            "| 항목 | 값 |",
            "|:--|:--|",
            f"| 부재 범위 | {scope_txt} |",
            "| 발전량·손실 | 관측 부재로 산출 불가 |",
            f"| 최근 동일 유형 이벤트 | {cnt}건 |",
            "",
        ]
        pr = to.get("presence") or []
        if pr:
            L += _mermaid_presence(pr)
            L += [""]

    L += _weather_table(w)
    if analysis and analysis.strip():
        L += ["## 분석", "", analysis.strip(), ""]  # 권고 조치 위, 숫자 없는 정성 서술
    L += _action_block(et)
    L += ["## 판단 유보", "", DISCLAIMER, ""]
    return "\n".join(L) + "\n"
