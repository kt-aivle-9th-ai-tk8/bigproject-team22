"""CLI 엔트리포인트 — event_id로 보고서 생성 → outputs/report_{type}_{id}.md 저장.

event_id 는 report_type 마다 가리키는 대상이 다르다(4개 타입이 fetch(event_id) 하나를
공유하는 계약이라 이름은 하나뿐). 의미는 registry.EVENT_ID_MEANING 이 정의하고,
--help 와 '없음' 메시지가 그 표를 그대로 인용한다.

사용법:
    python run.py 2                          # report_type 기본 anomaly
    python run.py 5001 --report-type defect  # defect 의 event_id 는 report_id
"""
import argparse
import os
import sys

# 콘솔 인코딩 (Windows cp949에서 한글/기호 깨짐 방지)
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

from app.core.config import OUTPUT_DIR, TRACING, LANGSMITH_PROJECT  # .env 로드 포함
from app.agents.graph import REPORT_TYPES
from app.agents.registry import EVENT_ID_MEANING
from app.service import generate_report


def main():
    ap = argparse.ArgumentParser(
        description="report_type 별 보고서 생성. event_id 의 의미는 타입마다 다르다:\n"
        + "\n".join(f"  {t:15s} {m}" for t, m in EVENT_ID_MEANING.items()),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "event_id",
        type=int,
        help="대상 식별자. --report-type 에 따라 의미가 다르다(위 설명 참고)",
    )
    ap.add_argument("--report-type", default="anomaly", choices=REPORT_TYPES)
    ap.add_argument("--start", default=None,
                    help="조회 기간 시작 YYYY-MM-DD (operation/farm_operation)")
    ap.add_argument("--end", default=None,
                    help="조회 기간 종료 YYYY-MM-DD, 해당 일 포함 (operation/farm_operation)")
    args = ap.parse_args()

    params = {k: v for k, v in (("period_start", args.start), ("period_end", args.end)) if v} or None

    if not os.getenv("OPENAI_API_KEY"):
        print("ERROR: OPENAI_API_KEY 가 설정되지 않았습니다 (.env 확인)")
        sys.exit(1)

    if TRACING:
        print(f"[run] LangSmith 트레이싱 ON (project={LANGSMITH_PROJECT})")

    print(f"[run] report_type={args.report_type} event_id={args.event_id}"
          + (f" params={params}" if params else "") + " 실행")
    res = generate_report(args.report_type, args.event_id, params)

    if res.get("error"):
        print(f"[run] 생성 실패(LLM 호출 오류): {res['error']}")
        sys.exit(2)

    if not res["found"]:
        # 대상이 없을 때 '무엇을 넣어야 하는지'까지 알려준다 — 타입마다 id 체계가 달라
        # 엉뚱한 id 를 넣어도 크래시 없이 여기로 떨어지기 때문이다.
        print(f"[run] {args.report_type} 에 event_id={args.event_id} 인 대상이 없습니다.")
        print(f"[run] {args.report_type} 의 event_id = {EVENT_ID_MEANING[args.report_type]}")
        sys.exit(1)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    suffix = f"_{args.start}_{args.end}" if (args.start or args.end) else ""
    out_path = os.path.join(OUTPUT_DIR, f"report_{args.report_type}_{args.event_id}{suffix}.md")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(res["draft"] or "")

    print(f"[run] verdict={res['verdict']} retry={res['retry_count']}")
    if res["issues"]:
        print(f"[run] issues={res['issues']}")
    if res.get("warnings"):
        print(f"[run] warnings(재시도 소진 후 통과 — 사람 확인 필요)={res['warnings']}")
    print(f"[run] 저장: {out_path}")


if __name__ == "__main__":
    main()
