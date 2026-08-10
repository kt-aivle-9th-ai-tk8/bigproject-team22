"""CLI 엔트리포인트 — event_id로 보고서 생성 → outputs/report_{type}_{id}.md 저장.

사용법:
    python run.py 2                       # report_type 기본 anomaly
    python run.py 2 --report-type anomaly
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
from app.service import generate_report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("event_id", type=int)
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
        print(f"[run] event_id={args.event_id} 없음. 종료")
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
