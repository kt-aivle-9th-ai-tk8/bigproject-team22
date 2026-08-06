"""defect 결정론적 렌더러 (LLM 없음) + LLM 분석용 팩트 제공.

역할 분담:
- 코드(여기): 핵심 지표·터빈별 상세·검출 이미지 목록·판단유보를 '주입'한다. = 사실의 단일 출처.
- LLM(defect_agent): '## 종합 분석'에서 코드가 준 facts를 근거로 종합·해석·우선순위 제시.
  LLM은 facts의 수치를 '인용'할 수 있으나 '생성'할 수 없다. critic이 exact-match로 검증.

권고 조치 섹션은 두지 않는다(심각도별 조치 기준이 아직 미정). 조치 단정은 critic이 반려한다.
"""
import json
import math
from urllib.parse import quote

from app.core.config import IMAGE_BASE_URL
from app.agents.verify import extract_numbers

SEVERITY_KO = {1: "경미", 2: "주의", 3: "중대", 4: "심각"}

DEFECT_TYPE_KO = {
    "Paint Damage": "도장 손상",
    "Contamination": "오염",
    "La Exposure": "라미네이트 노출",
    "La Damage": "라미네이트 손상",
    "Tape Damage": "보호테이프 손상",
    "Rain Collar": "레인칼라 손상",
    "Receptor Damage": "리셉터 손상",
    "Bond Crack": "접합부 균열",
    "Vortex Generator": "보텍스제너레이터 이상",
}

PART_SIDE_KO = {
    "LE": "앞전",
    "TE": "뒷전",
    "PS": "압력면",
    "SS": "흡입면",
}

STATUS_KO = {
    "UPLOADING": "이미지 업로드 중",
    "INSPECTING": "판독 중",
    "INSPECTED": "판독 완료",
}

# 터빈 섹션당 나열할 이미지 최대 개수. 초과분은 '생략' 건수를 명시한다(조용한 절단 금지).
MAX_IMAGES_PER_TURBINE = 30

DISCLAIMER = (
    "본 보고서의 결함은 AI 판독 모델이 검출·분류한 결과이며 확정된 손상 판정이 아닙니다. "
    "결함 확정과 조치 결정은 담당자의 현장 확인이 필요합니다."
)


def _sev(s) -> str:
    """4 → '심각(4)'."""
    s = int(s)
    return f"{SEVERITY_KO.get(s, s)}({s})"


def _sev_counts(counts: dict) -> str:
    if not counts:
        return "없음"
    return ", ".join(f"{_sev(k)} {v:,}건" for k, v in counts.items())


def _type_counts(counts: dict) -> str:
    if not counts:
        return "없음"
    return ", ".join(f"{DEFECT_TYPE_KO.get(k, k)} {v:,}건" for k, v in counts.items())


def _blade_counts(counts: dict) -> str:
    if not counts:
        return "없음"
    return ", ".join(f"{k}블레이드 {v:,}건" for k, v in counts.items())


def _side_counts(counts: dict) -> str:
    if not counts:
        return "없음"
    return ", ".join(f"{PART_SIDE_KO.get(k, k)}({k}) {v:,}건" for k, v in counts.items())


def _conf(c: dict) -> str:
    if not c:
        return "—"
    return f"{c['min']:.3f} ~ {c['max']:.3f} (평균 {c['mean']:.3f})"


def _severe_ratio(counts: dict, total: int):
    """심각도 3 이상 비율 — 분석이 자주 인용하는 지표라 코드가 미리 계산해 준다."""
    if not counts or not total:
        return None
    n = sum(v for k, v in counts.items() if int(k) >= 3)
    return f"{n:,}건 / 전체 {total:,}건 ({n / total * 100:.1f}%)"


def _top_type(top, n: int, total: int):
    """최다 결함 유형과 그 비율 — 분석이 자주 인용하는 지표라 코드가 미리 계산해 준다.

    비율을 안 주면 LLM이 '18건 / 27건 = 66.7%' 를 직접 계산하는데, 그 값은 facts에
    없으므로 critic이 환각으로 반려한다. 미리 줘서 계산할 이유 자체를 없앤다.
    """
    label = f"{DEFECT_TYPE_KO.get(top, top)} {n:,}건"
    if not total:
        return label
    return f"{label} / 전체 {total:,}건 ({n / total * 100:.1f}%)"


# ── 분포 차트 ────────────────────────────────────────────────────────────
# 표기 방식. 프론트 렌더러가 못 그리면 이 값만 바꾼다(로직은 그대로).
#   "bar"   : mermaid xychart-beta — 권장. 크기 비교가 정확하고 순서를 지킨다.
#             단 mermaid 10.3+ 필요(beta 문법).
#   "pie"   : mermaid pie — 구버전 호환용. 값이 비슷하면 조각 구분이 어렵다.
#   "table" : 마크다운 표 + 막대 문자 — mermaid 없이 어디서나 렌더된다.
CHART_STYLE = "bar"

# 막대가 1개뿐인 차트는 그리지 않는다. 정보가 없고 오히려 지저분하다
# (심각도가 1등급뿐인 점검이 실제로 다수 존재).
MIN_CHART_CATEGORIES = 2

TABLE_BAR_WIDTH = 22  # "table" 방식에서 최댓값 막대의 길이

# 막대 색. mermaid 기본색에 맡기지 않고 명시한다(다른 보고서 타입과 톤을 맞추려면 여기만 바꾼다).
# 계열이 하나뿐이라 색은 식별이 아니라 표시 역할만 한다 — 심각도 순서는 x축 순서가 나타낸다.
CHART_COLOR = "#3b82f6"


def _init_directive() -> str:
    """mermaid 테마 지시자. 중괄호를 손으로 세면 틀리므로 json.dumps 로 만든다."""
    theme = {"themeVariables": {"xyChart": {"plotColorPalette": CHART_COLOR}}}
    return "%%{init: " + json.dumps(theme, separators=(", ", ": ")) + "}%%"


# 제목은 chart_lines 가 마크다운 소제목으로 내보낸다 → 아래 렌더러들은 그림만 그린다
# (mermaid 내부 title 을 같이 쓰면 제목이 두 번 보인다).
def _xychart(pairs: list) -> list:
    ymax = max(1, math.ceil(max(v for _, v in pairs) * 1.2))
    return [
        "```mermaid",
        _init_directive(),
        "xychart-beta",
        "    x-axis [" + ", ".join(f'"{k}"' for k, _ in pairs) + "]",
        f'    y-axis "결함 수" 0 --> {ymax}',
        "    bar [" + ", ".join(str(v) for _, v in pairs) + "]",
        "```",
    ]


def _pie(pairs: list) -> list:
    return [
        "```mermaid",
        "pie showData",
        *[f'    "{k}" : {v}' for k, v in pairs],
        "```",
    ]


def _bar_table(pairs: list) -> list:
    total = sum(v for _, v in pairs)
    top = max(v for _, v in pairs)
    lines = ["| | 결함 | 비율 | |", "|---|---:|---:|---|"]
    for k, v in pairs:
        bar = "█" * max(1, round(v / top * TABLE_BAR_WIDTH))
        lines.append(f"| {k} | {v:,}건 | {v / total * 100:.1f}% | `{bar}` |")
    return lines


def chart_lines(title: str, pairs: list) -> list:
    """제목 + 분포 차트 1개. 카테고리가 2종 미만이면 제목까지 통째로 안 그린다.

    제목을 차트와 한 덩어리로 반환하는 게 핵심이다. 차트가 없으면 제목도 같이 사라지므로
    '제목만 남는' 경우가 구조적으로 생길 수 없다(실데이터 59건 중 차트 0개가 18건,
    둘 중 하나만 그려지는 건이 6건 — 어느 쪽이 빠질지는 점검마다 다르다).

    pairs 는 [(라벨, 건수)] 이며 넘어온 순서를 그대로 지킨다
    (심각도는 심각→경미, 유형은 많은 순).
    """
    pairs = [(k, v) for k, v in pairs if v]
    if len(pairs) < MIN_CHART_CATEGORIES:
        return []
    render = {"bar": _xychart, "pie": _pie}.get(CHART_STYLE, _bar_table)
    return [f"### {title}", "", *render(pairs)]


def overview_charts(to) -> list:
    """핵심 지표 표 아래에 붙는 분포 차트 — 심각도, 결함 유형."""
    s = to.get("summary", {}) or {}
    total = s.get("n_defects_total", 0)
    if not total:
        return []

    lines = []
    for chart in (
        chart_lines(
            f"심각도 분포 (총 {total:,}건)",
            [(_sev(k), v) for k, v in (s.get("severity_counts") or {}).items()],
        ),
        chart_lines(
            f"결함 유형 분포 (총 {total:,}건)",
            [(DEFECT_TYPE_KO.get(k, k), v) for k, v in (s.get("type_counts") or {}).items()],
        ),
    ):
        if chart:
            lines += ["", *chart]
    return lines


def report_title(e) -> str:
    """'1발전소 2023-04-19 점검 보고서'.

    점검일자는 inspection_start 의 날짜 부분. 점검이 자정을 넘겨 이틀에 걸치는 건이
    있어 '시작일' 기준으로 고정한다(정확한 구간은 부제의 '점검 기간'에 그대로 남는다).
    발전소는 현재 windfarm_id(숫자)뿐이라 'N발전소'로 쓴다. 발전소명 컬럼이 생기면
    여기만 이름으로 바꾸면 된다.
    """
    date = (e.get("inspection_start") or "")[:10] or "일자 미상"
    return f"{e.get('windfarm_id')}발전소 {date} 점검 보고서"


def subtitle_lines(to) -> list:
    """제목 밑 부제 — 점검 식별·기간. anomaly 의 `유형` · 상태 · 발생시각 줄과 같은 자리.

    보고서 부제에만 쓰고 LLM facts에는 넣지 않는다 → 분석이 점검번호·날짜를 다시 쓰면
    allowed_numbers에 없어 환각 후보로 걸린다(의도된 동작).
    """
    e = to["event"]
    status = e.get("status")
    return [
        f"`점검 #{e.get('inspection_id')}` · 보고서 {e.get('report_id')} · "
        f"단지 {e.get('windfarm_id')} · {STATUS_KO.get(status, status)}",
        "",
        f"점검 기간 {e.get('inspection_start')} ~ {e.get('inspection_end')}",
    ]


def stat_pairs(to) -> list:
    """전체 집계 (지표, 값) 쌍 — 핵심 지표 표와 LLM facts의 단일 출처. 수치 전부 코드 주입.

    표(metric_table)와 불릿(stat_lines)은 이 목록을 각자 렌더링만 한다.
    새 지표는 여기에 한 번만 추가하면 양쪽에 동시에 반영된다.
    """
    s = to.get("summary", {}) or {}
    pairs = []

    # inspection에 '점검 대상 터빈' 목록이 없어, 결함이 검출된 터빈만 알 수 있다.
    codes = s.get("turbine_codes") or []
    pairs.append((
        "결함 검출 터빈",
        f"{s.get('n_turbines', 0)}대" + (f" ({', '.join(codes)})" if codes else ""),
    ))
    pairs.append((
        "검출 결함",
        f"{s.get('n_defects_total', 0):,}건 / 이미지 {s.get('n_images_total', 0):,}장",
    ))

    if s.get("n_defects_total"):
        pairs.append(("심각도 분포", _sev_counts(s.get("severity_counts", {}))))
        sr = _severe_ratio(s.get("severity_counts", {}), s.get("n_defects_total", 0))
        if sr:
            pairs.append(("심각도 3 이상", sr))
        pairs.append((
            "최다 결함 유형",
            _top_type(
                s.get("top_defect_type"),
                s.get("top_defect_type_n", 0),
                s.get("n_defects_total", 0),
            ),
        ))
        pairs.append((
            "결함 최다 터빈",
            f"{s.get('worst_turbine')} {s.get('worst_turbine_n', 0):,}건",
        ))
        pairs.append(("검출 신뢰도", _conf(s.get("confidence", {}))))

    zero = s.get("zero_defect_turbines") or []
    if zero:
        pairs.append(("결함 미검출 터빈", f"{', '.join(zero)} (상세 없음)"))
    return pairs


def stat_notes(to) -> list:
    """지표가 아닌 단서 문구 — 값이 없어 (지표, 값) 쌍으로 못 만드는 것들."""
    notes = []
    if to["event"].get("status") != "INSPECTED":
        notes.append("판독이 완료되지 않아 결과가 변경될 수 있음")
    if not (to.get("summary", {}) or {}).get("n_defects_total"):
        notes.append("검출된 결함 없음")
    return notes


def stat_lines(to) -> list:
    """전체 집계 불릿 — LLM facts 전용. 표와 같은 stat_pairs 를 불릿으로 편다."""
    notes = stat_notes(to)
    # '검출된 결함 없음'은 건수 라인 뒤에 와야 읽힌다(불릿 순서 유지).
    head = [f"- {n}" for n in notes if n != "검출된 결함 없음"]
    tail = [f"- {n}" for n in notes if n == "검출된 결함 없음"]
    body = [f"- {k}: {v}" for k, v in stat_pairs(to)]
    # 결함 0건이면 '검출된 결함 없음'이 집계 뒤·미검출 터빈 앞에 들어간다.
    if tail:
        cut = len(body) - 1 if body and body[-1].startswith("- 결함 미검출 터빈") else len(body)
        body = body[:cut] + tail + body[cut:]
    return head + body


def metric_table(to) -> list:
    """## 핵심 지표 표 — anomaly/builder.py 의 `| 지표 | 값 |` 형식과 통일.

    표에 못 넣는 단서 문구(stat_notes)는 표 아래 인용문으로 남긴다 — 조용히 버리지 않는다.
    """
    lines = ["## 핵심 지표", "", "| 지표 | 값 |", "|:--|--:|"]
    for k, v in stat_pairs(to):
        # 검출 결함 건수가 이 보고서의 머리 수치 — anomaly 가 놓친 발전량을 굵게 쓰는 것과 같다.
        bold = k == "검출 결함"
        lines.append(f"| **{k}** | **{v}** |" if bold else f"| {k} | {v} |")
    notes = stat_notes(to)
    if notes:
        # 표 바로 뒤에 '>' 를 붙이면 렌더러에 따라 표가 끊기지 않는다 — 빈 줄로 분리.
        lines += ["", *[f"> {n}" for n in notes]]
    return lines


def turbine_pairs(t) -> list:
    """터빈 1대의 (지표, 값) 쌍 — 상세 표와 LLM facts의 단일 출처. stat_pairs 와 같은 구조.

    이미지 목록은 제외(양이 많고 해석 대상이 아니라 facts에 넣지 않는다).
    """
    return [
        ("결함", f"{t['n_defects']:,}건"),
        ("이미지", f"{t['n_images']:,}장"),
        ("최고 심각도", _sev(t["max_severity"])),
        ("심각도별", _sev_counts(t.get("severity_counts", {}))),
        ("유형별", _type_counts(t.get("type_counts", {}))),
        ("블레이드별", _blade_counts(t.get("blade_counts", {}))),
        ("위치별", _side_counts(t.get("side_counts", {}))),
        ("검출 신뢰도", _conf(t.get("confidence", {}))),
    ]


def turbine_summary_lines(t) -> list:
    """터빈 1대의 요약 불릿 — LLM facts 전용. 표와 같은 turbine_pairs 를 불릿으로 편다."""
    return [f"- {k}: {v}" for k, v in turbine_pairs(t)]


def turbine_table(t) -> list:
    """터빈 1대의 상세 표 — 핵심 지표 표와 같은 `| 지표 | 값 |` 형식."""
    lines = ["| 지표 | 값 |", "|:--|--:|"]
    for k, v in turbine_pairs(t):
        # 결함 건수가 이 섹션의 머리 수치 — 핵심 지표 표의 '검출 결함'과 같은 역할.
        bold = k == "결함"
        lines.append(f"| **{k}** | **{v}** |" if bold else f"| {k} | {v} |")
    return lines


def image_url(image_path: str) -> str:
    """image_path → 보고서에 넣을 이미지 URL. 넣을 수 없으면 빈 문자열.

    IMAGE_BASE_URL 미설정이면 ''를 돌려주고, 렌더러는 캡션 텍스트만 남긴다
    (이미지 링크가 전부 깨져 보이는 것보다 낫다).
    RDS 전환 후 image_path 에 전체 URL이 들어오는 경우도 그대로 통과시킨다.
    """
    if not image_path:
        return ""
    if image_path.startswith(("http://", "https://", "file://")):
        return image_path
    if not IMAGE_BASE_URL:
        return ""
    # quote 는 '/' 를 보존하므로 경로 구분자는 유지되고 공백·한글만 인코딩된다.
    return _md_safe(f"{IMAGE_BASE_URL}/{quote(image_path.lstrip('/'))}")


def _md_safe(url: str) -> str:
    """마크다운 링크를 깨뜨리는 문자를 퍼센트 인코딩.

    '![](...)' 의 괄호와 충돌하므로 URL 안의 소괄호·공백은 반드시 인코딩해야 한다
    (로컬 file:// 경로에 '5주차(0727-0731)' 같은 디렉토리명이 들어오는 경우).
    quote 가 만든 '%' 는 건드리지 않으므로 이중 인코딩되지 않는다.
    """
    for ch, esc in ((" ", "%20"), ("(", "%28"), (")", "%29")):
        url = url.replace(ch, esc)
    return url


def _image_caption(img, turbine_code: str) -> str:
    """'U1 · A블레이드 · 앞전(LE) · 2번째 사진' — 이미지 식별자. alt 텍스트로도 쓴다."""
    side = img.get("part_side")
    return (
        f"{turbine_code} · {img.get('blade_tag')}블레이드 · "
        f"{PART_SIDE_KO.get(side, side)}({side}) · {img.get('seq')}번째 사진"
    )


def _image_line(img, turbine_code: str) -> str:
    """'- U1 · A블레이드 · 앞전(LE) · 2번째 사진 — 도장 손상 심각(4) 2건 · 신뢰도 0.900~0.970'."""
    found = ", ".join(
        f"{DEFECT_TYPE_KO.get(x['defect_type'], x['defect_type'])} {_sev(x['severity'])} {x['n']:,}건"
        for x in img.get("types_by_severity", [])
    )
    return (
        f"- {_image_caption(img, turbine_code)} "
        f"— {found} · 신뢰도 {img['conf_min']:.3f}~{img['conf_max']:.3f}"
    )


def _image_block(img, turbine_code: str) -> list:
    """이미지 1장 = 캡션 줄 + 실제 이미지.

    이미지는 두 칸 들여써서 위 캡션 항목에 딸린 내용으로 렌더링되게 한다.
    URL을 만들 수 없으면 캡션 줄만 남는다.
    """
    lines = [_image_line(img, turbine_code)]
    url = image_url(img.get("image_path"))
    if url:
        lines += ["", f"  ![{_image_caption(img, turbine_code)}]({url})", ""]
    return lines


def turbine_image_lines(t) -> list:
    """터빈 1대의 검출 이미지 목록 (심각도 높은 순). 초과분은 생략 건수를 명시."""
    images = t.get("images", []) or []
    shown = images[:MAX_IMAGES_PER_TURBINE]
    lines = []
    for img in shown:
        lines += _image_block(img, t["turbine_code"])
    if len(images) > len(shown):
        lines.append(f"- …외 {len(images) - len(shown):,}장 생략 (전체는 대시보드에서 확인)")
    while lines and not lines[-1]:  # 섹션 끝 빈 줄 정리
        lines.pop()
    return lines


def fact_lines(to) -> list:
    """LLM 분석용 팩트 블록 = 전체 집계 + 터빈별 요약.

    LLM은 이 블록의 수치만 인용할 수 있다(생성 금지). allowed_numbers와 같은 출처.
    - 점검번호·기간(subtitle_lines)은 제외 → 분석이 다시 쓰면 critic이 잡는다.
    - 이미지 목록도 제외(양이 많고 해석 대상이 아님). 보고서 본문에는 코드가 렌더링.
    """
    lines = list(stat_lines(to))
    for t in to.get("turbines", []) or []:
        if t["n_defects"] == 0:
            continue
        lines.append(f"[{t['turbine_code']}]")
        lines += turbine_summary_lines(t)
    return lines


def allowed_numbers(to) -> set:
    """분석이 인용해도 되는 수치(절대값) 집합. 코드가 생성한 팩트에서만 추출.

    부호는 자연어에서 자주 뒤집히므로 절대값으로 비교한다.
    점검 기간(날짜·시각)은 부제에만 있고 facts에는 넣지 않는다
    → 분석이 날짜를 다시 쓰면 환각 후보로 걸린다(의도된 동작).
    """
    text = "\n".join(fact_lines(to))
    return {abs(n) for n in extract_numbers(text)}


def render_report(to, analysis: str) -> str:
    """전체 보고서 조립.

    코드 주입: 핵심 지표·터빈별 상세·검출 이미지·판단유보.  LLM: 종합 분석 섹션만.
    결함 0건 터빈은 상세 섹션을 만들지 않고 핵심 지표 표에만 표기한다.
    """
    e = to["event"]
    parts = [
        f"# {report_title(e)}",
        "",
        *subtitle_lines(to),
        "",
        *metric_table(to),
        *overview_charts(to),
        "",
        "## 종합 분석",
        (analysis or "").strip(),
    ]

    for t in to.get("turbines", []) or []:
        if t["n_defects"] == 0:
            continue
        parts += [
            "",
            "---",
            "",
            f"## {t['turbine_code']} — 결함 상세",
            "",
            *turbine_table(t),
            "",
            "### 검출 이미지",
            *turbine_image_lines(t),
        ]

    parts += ["", "## 판단 유보", DISCLAIMER]
    return "\n".join(parts) + "\n"
