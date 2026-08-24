"""보고서 종류별로 어떤 테이블을 몇 행 읽는지 보여준다.

왜 필요한가: 조회가 범위 조회(WHERE)로 좁혀졌는지는 결과물만 봐서는 알 수 없다.
보고서는 똑같이 나오면서 뒤에서 테이블을 통째로 읽고 있을 수 있다(#89 이전이 그랬다).
이 스크립트는 보고서 1건이 실제로 몇 행을 읽는지 세어 그 차이를 드러낸다.

사용:
    DB_URL=... python scripts/check_sources.py

LLM 은 호출하지 않는다(비용 0). 종합분석 없이 데이터 조회 경로만 본다.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# setdefault 가 아니라 강제 대입이어야 한다 — 셸/.env 에 true 가 있으면 setdefault 는 그대로 두고,
# 그러면 에이전트가 분석 경로로 들어가 아래 _NoLLM 이 터진다(조회 경로 대신 실패만 찍힘).
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

CASES = [("defect", 5001), ("anomaly", 1), ("operation", 11), ("farm_operation", 4)]


def main():
    print(f"등재 테이블 {len(ds._RDS_TABLES)}개: {sorted(ds._RDS_TABLES)}")
    print(f"캐시 대상(참조표): {sorted(ds._REFERENCE)}")
    print()

    failed = []
    for rt, eid in CASES:
        # 캐시를 비워야 '이 보고서가 실제로 읽는 양'이 잡힌다(참조표는 캐시되므로).
        ds.reset_cache()
        try:
            r = generate_report(rt, eid)
            err = r.get("error")
        except Exception as e:
            err = f"{type(e).__name__}: {e}"

        stats = ds.reads()
        total = sum(rows for _, rows in stats.values())
        print(f"[{rt}] event_id={eid}  읽은 행 합계 {total:,}")
        for name, (calls, rows) in sorted(stats.items(), key=lambda kv: -kv[1][1]):
            print(f"    {name:16s} {rows:>9,} 행  ({calls}회 조회)")
        if err:
            print(f"    [실패] {err[:80]}")
            failed.append(rt)
        print()

    if failed:
        print(f"생성 실패한 보고서: {failed}")
        return 1
    print("4종 모두 정상 생성.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
