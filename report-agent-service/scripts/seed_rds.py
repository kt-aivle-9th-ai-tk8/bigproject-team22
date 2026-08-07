"""data/*.csv → RDS 시드 적재.

적재 대상은 '결함 진단(defect) 보고서 체인' 전부다:
    wind_farm → turbine_model → turbine → blade → users → report → inspection → defect
이 순서는 FK 의존 순서라 바꾸면 안 된다.

적재하지 않는 것과 그 이유 (CSV 데이터가 ERD 와 어긋난다):
  scada_record  : CSV 는 turbine_code(U1~U8)로 터빈을 가리키는데 ERD/DB 는 turbine_id FK 다.
                  turbine 테이블에는 U1~U3 뿐(3개 단지 × 3대)이라 U4~U8 은 대응 터빈이 없고
                  (140,176행 중 87,610행 = 62%), U1~U3 도 3개 단지에 중복이라 어느 단지인지
                  결정할 수 없다. 매핑 규칙이 정해지기 전에는 적재하면 안 된다.
  anomaly_event : 같은 문제 (339행 중 171행 = 50%가 U4~U8).
  aws_record    : FK 는 없어 적재 자체는 되지만, 관측소가 741 하나뿐인데
                  wind_farm.aws_station_id 는 101/102/103 이라 어느 단지와도 조인되지 않는다.
                  --with-aws 로 넣을 수 있게만 해두고 기본은 제외한다.

CSV 에 없어 합성하는 값 (지어낸 값은 여기 한 곳에만 있다):
  users.employee_id   EMP0001 형식 — user_id 로 만든 결정론적 사번
  users.password      로그인 불가능한 자리표시자. 시드 계정으로 로그인되면 안 된다.
  users.created_at/updated_at, login_fail_count
  report.wind_farm_id 소속 inspection 의 터빈 → 단지 (보고서 60건 모두 단지가 1개로 확정됨)
  report.period_start MIN(inspection_start), period_end MAX(inspection_end)
  report 의 title/content/generated_at/turbine_id/approver_id/event_id 는 채우지 않는다(NULL).
    본문은 report-agent 가 생성하는 것이라 여기서 지어내면 안 된다.

사용:
    python scripts/seed_rds.py --db-url mysql+pymysql://user:pw@host:3306/db
    python scripts/seed_rds.py --db-url ... --replace     # 기존 행 지우고 다시
    python scripts/seed_rds.py --db-url ... --dry-run     # 적재 없이 검증만
DB_URL 을 생략하면 환경변수 DB_URL 을 쓴다.
"""
import argparse
import os
import sys

import pandas as pd
from sqlalchemy import create_engine, text

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(HERE, "data")

# 적재 순서 = FK 의존 순서. 삭제는 이 역순.
ORDER = ["wind_farms", "turbine_models", "turbines", "blades", "users",
         "report", "inspection", "defect"]

# 로그인 불가 자리표시자. bcrypt 해시 형식이 아니므로 어떤 비밀번호로도 매칭되지 않는다.
SEED_PASSWORD = "!SEED-NO-LOGIN"


def _read(name, **kw):
    return pd.read_csv(os.path.join(DATA_DIR, f"{name}.csv"), encoding="utf-8-sig", **kw)


def build_frames(with_aws: bool) -> dict:
    """CSV → 테이블별 DataFrame(DB 컬럼명·순서에 맞춤)."""
    wf = _read("wind_farm")
    tm = _read("turbine_model")
    tb = _read("turbine")
    bl = _read("blade")
    us = _read("user")
    rp = _read("report")
    insp = _read("inspection", parse_dates=["inspection_start", "inspection_end", "created_at"])
    df = _read("defect", parse_dates=["created_at"])

    now = pd.Timestamp.now().floor("s")

    # users — CSV 에 없는 필수 컬럼을 합성한다(위 docstring 참고).
    users = pd.DataFrame({
        "user_id": us["user_id"],
        "employee_id": us["user_id"].map(lambda i: f"EMP{int(i):04d}"),
        "password": SEED_PASSWORD,
        "user_name": us["user_name"],
        "department": None,
        "phone": None,
        "role": us["role"],
        "status": us["status"],
        "login_fail_count": 0,
        "latest_session_id": None,
        "created_at": now,
        "updated_at": now,
    })

    # report — wind_farm_id·period_* 는 소속 inspection 에서 역산한다.
    j = insp.merge(tb[["turbine_id", "wind_farm_id"]], on="turbine_id", how="left")
    agg = j.groupby("report_id").agg(
        wind_farm_id=("wind_farm_id", "first"),
        n_farm=("wind_farm_id", "nunique"),
        period_start=("inspection_start", "min"),
        period_end=("inspection_end", "max"),
    )
    bad = agg[agg["n_farm"] != 1]
    if len(bad):
        raise SystemExit(
            f"[중단] 보고서 {len(bad)}건이 여러 단지에 걸쳐 있어 wind_farm_id 를 확정할 수 없다: "
            f"{bad.index.tolist()[:10]}"
        )

    report = rp.merge(agg, left_on="report_id", right_index=True, how="left")
    missing = report[report["wind_farm_id"].isna()]
    if len(missing):
        raise SystemExit(
            f"[중단] 점검이 하나도 달리지 않은 보고서 {len(missing)}건 — "
            f"wind_farm_id(NOT NULL)를 만들 수 없다: {missing['report_id'].tolist()[:10]}"
        )

    report = pd.DataFrame({
        "report_id": report["report_id"],
        "wind_farm_id": report["wind_farm_id"].astype("int64"),
        "turbine_id": None,        # defect 보고서는 터빈 여러 대를 묶을 수 있어 비운다
        "approver_id": None,       # 미승인
        "anomaly_event_id": None,  # 이상감지 보고서가 아님
        "report_type": report["report_type"],
        # 시각까지 그대로 넣는다(DB 가 DATETIME) — 점검이 자정을 넘기는 건이 있어 날짜로 자르면 구간이 뭉개진다.
        "period_start": report["period_start"],
        "period_end": report["period_end"],
        "title": None,
        "context": None,           # 본문은 report-agent 가 생성한다 — 지어내지 않는다
        "status": report["status"],
        "generated_at": None,
    })

    frames = {
        "wind_farms": wf,
        "turbine_models": tm,
        "turbines": tb,
        "blades": bl,
        "users": users,
        "report": report,
        "inspection": insp,
        "defect": df,
    }
    if with_aws:
        frames["aws_record"] = _read("aws_record", parse_dates=["timestamp"])
    return frames


def check_fk(frames: dict):
    """적재 전에 CSV 안에서 FK 가 성립하는지 본다. DB 가 거부하기 전에 우리가 먼저 잡는다."""
    def missing(child, ccol, parent, pcol):
        return sorted(set(frames[child][ccol].dropna()) - set(frames[parent][pcol]))

    checks = [
        ("turbines", "wind_farm_id", "wind_farms", "wind_farm_id"),
        ("turbines", "turbine_model_id", "turbine_models", "turbine_model_id"),
        ("blades", "turbine_id", "turbines", "turbine_id"),
        ("report", "wind_farm_id", "wind_farms", "wind_farm_id"),
        ("inspection", "turbine_id", "turbines", "turbine_id"),
        ("inspection", "user_id", "users", "user_id"),
        ("inspection", "report_id", "report", "report_id"),
        ("defect", "inspection_id", "inspection", "inspection_id"),
        ("defect", "blade_id", "blades", "blade_id"),
    ]
    bad = [(c, cc, p, m) for c, cc, p, pc in checks if (m := missing(c, cc, p, pc))]
    for c, cc, p, m in bad:
        print(f"  ✗ {c}.{cc} → {p}: 부모에 없는 값 {m[:10]}{' ...' if len(m) > 10 else ''}")
    if bad:
        raise SystemExit("[중단] FK 위반 — 적재하면 DB 가 거부한다.")
    print("  ✓ FK 무결성 통과")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db-url", default=os.getenv("DB_URL", ""))
    ap.add_argument("--replace", action="store_true", help="기존 행을 지우고 다시 적재")
    ap.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 검증만")
    ap.add_argument("--with-aws", action="store_true",
                    help="aws_record 도 적재(관측소 id 가 wind_farm 과 안 맞는 것을 알고도 넣을 때)")
    a = ap.parse_args()

    print("### 1) CSV 읽기 + 파생값 생성")
    frames = build_frames(a.with_aws)
    for t in frames:
        print(f"  {t:16s} {len(frames[t]):>7,} 행")

    print("\n### 2) FK 검증(적재 전)")
    check_fk(frames)

    if a.dry_run:
        print("\n### dry-run — 여기서 종료(DB 미변경)")
        return

    if not a.db_url:
        raise SystemExit("[중단] --db-url 또는 환경변수 DB_URL 이 필요하다.")

    engine = create_engine(a.db_url, pool_pre_ping=True)
    order = [t for t in ORDER if t in frames] + (["aws_record"] if a.with_aws else [])

    print("\n### 3) 적재")
    with engine.begin() as conn:          # 전부 성공하거나 전부 롤백
        counts = {t: conn.execute(text(f"SELECT COUNT(*) FROM {t}")).scalar() for t in order}
        nonempty = {t: n for t, n in counts.items() if n}
        if nonempty and not a.replace:
            raise SystemExit(
                f"[중단] 이미 데이터가 있다: {nonempty}\n"
                f"        지우고 다시 넣으려면 --replace 를 붙일 것."
            )
        if a.replace:
            for t in reversed(order):      # 자식부터 지워야 FK 에 안 걸린다
                n = conn.execute(text(f"DELETE FROM {t}")).rowcount
                if n:
                    print(f"  - {t:16s} {n:>7,} 행 삭제")

        for t in order:
            frames[t].to_sql(t, conn, if_exists="append", index=False, chunksize=1000)
            print(f"  + {t:16s} {len(frames[t]):>7,} 행 적재")

    print("\n### 4) 확인")
    with engine.connect() as conn:
        for t in order:
            n = conn.execute(text(f"SELECT COUNT(*) FROM {t}")).scalar()
            ok = "✓" if n == len(frames[t]) else "✗"
            print(f"  {ok} {t:16s} DB {n:>7,} 행 (CSV {len(frames[t]):,})")

    print("\n### 완료")


if __name__ == "__main__":
    sys.exit(main())
