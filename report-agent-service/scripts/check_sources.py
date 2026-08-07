"""보고서 종류별로 어떤 테이블을 CSV/RDS 어디에서 읽는지 보여준다.

왜 필요한가: 등재되지 않은 테이블은 조용히 CSV 로 떨어진다. 보고서는 멀쩡히 나오므로
'RDS 로 돌렸다'와 '실은 CSV 였다'를 눈으로는 구별할 수 없다. 새 테이블을 RDS 로 옮긴 뒤
정말 넘어갔는지 확인하는 것이 이 스크립트의 용도다.

사용:
    python scripts/check_sources.py
    DATA_SOURCE=rds DB_URL=... python scripts/check_sources.py
    ... STRICT_RDS=true python scripts/check_sources.py   # CSV 로 떨어지면 실패시킴

LLM 은 호출하지 않는다(비용 0). 종합분석 없이 데이터 조회 경로만 본다.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# setdefault 가 아니라 강제 대입이어야 한다 — 셸/.env 에 true 가 있으면 setdefault 는 그대로 두고,
# 그러면 에이전트가 분석 경로로 들어가 아래 _NoLLM 이 터진다(데이터 출처 대신 실패만 찍힘).
# 이 스크립트는 조회 경로만 보는 게 목적이라 분석은 항상 꺼야 한다.
os.environ["REPORT_WITH_ANALYSIS"] = "false"

import app.agents.llm as L  # noqa: E402


class _NoLLM:
    """LLM 이 불리면 즉시 드러나게 한다 — 이 스크립트는 데이터 경로만 본다."""

    def with_structured_output(self, *a, **k):
        return self

    def invoke(self, *a, **k):
        raise AssertionError("LLM 이 호출됐다 — REPORT_WITH_ANALYSIS 가 안 꺼졌다")


L.llm = _NoLLM()

from app.core import datasource as ds        # noqa: E402
from app.service import generate_report      # noqa: E402

CASES = [("defect", 5001), ("operation", 2), ("farm_operation", 1), ("anomaly", 1)]


def main():
    print(f"DATA_SOURCE={ds.DATA_SOURCE}  USE_RDS={ds.USE_RDS}  STRICT_RDS={ds.STRICT_RDS}")
    print(f"_RDS_TABLES 등재: {sorted(ds._RDS_TABLES)}")
    print()
    print(f"{'보고서':<16}{'RDS 에서':<52}{'CSV 에서'}")
    print("-" * 96)

    all_csv, failed = set(), []
    for rt, eid in CASES:
        ds.reset_cache()          # 보고서마다 새로 읽게 해야 '이 보고서가 쓰는 테이블'이 정확히 잡힌다
        try:
            r = generate_report(rt, eid)
            err = r.get("error")
        except Exception as e:
            err = f"{type(e).__name__}: {e}"

        src = ds.sources()
        rds = sorted(t for t, v in src.items() if v == "RDS")
        csv = sorted(t for t, v in src.items() if v == "CSV")
        all_csv |= set(csv)

        print(f"{rt:<16}{', '.join(rds) or '—':<52}{', '.join(csv) or '—'}")
        n = len(rds) + len(csv)
        pct = round(len(rds) / n * 100) if n else 0
        line = f"{'':<16}→ RDS {pct}% ({len(rds)}/{n} 테이블)"
        if err:
            line += f"  [실패] {err[:60]}"
            failed.append(rt)
        print(line)

    print("-" * 96)
    if all_csv:
        print(f"아직 CSV 인 테이블: {sorted(all_csv)}")
        print("  → 이것들이 RDS 로 넘어가야 전 보고서가 100% RDS 가 된다.")
    else:
        print("모든 테이블이 RDS 에서 온다.")

    if failed:
        print(f"\n생성 실패한 보고서: {failed}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
