#!/usr/bin/env python3
"""원본 SCADA 2종 → 통합 시간단위 CSV(scada_hourly.csv.gz) 생성기.

  python3 backend/src/main/resources/db/seed/build_scada_hourly.py \
      --jangheung "<장흥 원본.csv>" --hwasun "<화순 원본.csv>" \
      --out backend/src/main/resources/db/seed/scada_hourly.csv.gz

기존 통합본을 재생성하는 목적이다. 이전 통합본에는 화순 발전량 80,324건(8.81%)이 빈 값으로
들어가 있었는데, 원본을 세어 보면 빈 값이 **0건**이다 — 변환 과정에서 유실된 것이라 다시 만든다.

── 원본 두 파일의 차이 ──────────────────────────────────────────────────────
둘 다 13열이고 컬럼 구성은 같지만 시각 컬럼의 이름과 해상도가 다르다.

  장흥  '일자'          2022-01-01 00:00   2022-01 ~ 2024-12 는 10분 단위, 2025-01 부터 정시
  화순  '일자(1시간단위)' 2015-12-01 00      전 구간 정시(분 표기 자체가 없다)

10분 단위 구간은 **시간 평균**으로 접는다. 합계가 아니다 — 계통 유효 전력은 kW(순시 출력)라
6개 표본을 더하면 6배가 된다. 평균 kW × 1h = kWh 이므로 daily_generation 의 SUM(power_output)
관례와도 맞는다. 원본 2025 구간의 값이 이미 5.821666667 / 733.1733333 처럼 6자리 순환소수인
것으로 보아, 원본 제공자도 같은 방식(10분 6개의 평균)으로 정시 값을 만든 것으로 보인다.

── turbine_id 매핑 ──────────────────────────────────────────────────────────
V10__reset_and_seed_master_data.sql 의 시드와 일치시킨다. 원본에는 발전호기(1..N)만 있고
단지 구분이 파일로 나뉘어 있으므로 여기서 전역 id 로 편다.

  장흥 호기 1~6  → turbine_id 1~6    (wind_farm 1, code '1'~'6')
  화순 호기 1~8  → turbine_id 7~14   (wind_farm 2, code 'U1'~'U8')

이 매핑이 V10 과 어긋나면 FK 위반이나 조용한 오적재가 되므로, 시드를 바꿀 때 여기도 함께 본다.

── 출력 ────────────────────────────────────────────────────────────────────
헤더 없는 CSV, gzip. 열 순서:

  turbine_id,recorded_at,power_output,wind_speed

**wind_speed 를 새로 싣는다.** 원본에 '나셀 풍속(초당 미터)' 이 있는데 이전 통합본은 3열만
남기고 버렸다. V4 가 scada_record.wind_speed 컬럼을 이미 만들어 두었고, 기대발전량 예측
(serving/ml/predict.py: norm_wind_speed(wind_speed, ...))이 이 값을 필수로 요구한다.
→ 4열이 되므로 V11__seed_scada_history.java 의 파서도 함께 고쳐야 한다(아래 '적용' 참고).
   3열 그대로가 필요하면 --no-wind-speed 로 뺄 수 있다.

원본에 아예 행이 없는 시각은 **power_output 이 빈 값(=NULL)인 행으로 채운다**(--no-gap-fill 로 끌 수 있다).
호기별 [최초 관측, 최종 관측] 구간을 1시간 격자로 메우므로, 적재 후 scada_record 는 그 구간에
빠진 시각이 없다. 관측 자체가 없던 앞뒤 바깥은 만들지 않는다 — 없던 계열을 지어내는 것이기 때문이다.
2026 구간은 V12 가 2025 를 복사해 만들므로 여기서 격자를 세우면 2026 도 자동으로 채워진다.

주의 — 이 격자가 바꾸는 계약: anomaly-detection-service 의 events.find_gaps() 는 '행의 부재'를
data_missing 신호로 쓴다(\"데이터 행이 없는 것 자체가 부재 신호다\"). 격자를 채우면 그 판정이
행 부재로는 성립하지 않으므로, 소비자 쪽을 `power_output IS NULL` 기준으로 바꿔야 한다.
같은 가정을 쓰는 곳: report-agent 의 get_farm_presence(부재창 호기별 관측 행 수),
backend PowerQueryService.currentPowerByTurbines(미적재 터빈 경고).
"""
import argparse
import csv
import gzip
import sys
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

ENCODING = "cp949"  # 원본 2종 모두. UTF-8 로 열면 한글 헤더에서 깨진다.

TS, UNIT, WS, PW = 0, 2, 3, 4  # 원본 열 위치 (일자, 발전호기, 나셀풍속, 계통유효전력)

FARMS = {
    # 파일 별칭 → (turbine_id 오프셋, 기대 호기 수)
    "jangheung": (0, 6),   # 호기 1~6 → id 1~6
    "hwasun": (6, 8),      # 호기 1~8 → id 7~14
}


def to_float(raw: str) -> float:
    """숫자 문자열 → float. **천단위 쉼표를 반드시 걷어낸다.**

    화순 원본은 |값| >= 1000 을 예외 없이 `"1,291.20"` 처럼 쉼표로 적는다(80,324건).
    이전 통합본이 바로 이 값들에서 float() 에 실패해 전부 빈 값으로 남겼다 — 화순 발전량의
    8.81%, 그것도 **고출력 구간만** 통째로 사라진 것이라 기대발전량 모델과 이상탐지가
    함께 왜곡된다. 장흥 원본은 쉼표를 쓰지 않아 이 사고를 겪지 않았다.
    """
    return float(raw.replace(",", ""))


def fmt(x: float) -> str:
    """소수점 이하 6자리까지, 뒤따르는 0 은 떼고 적는다.

    유효숫자 지정(%g)을 쓰면 안 된다 — 1197.207333 이 %.6g 에서 '1197.21' 이 되어
    1000 이상 구간만 조용히 자릿수를 잃는다(화순 고출력 구간이 통째로 해당된다).
    6자리로 잡은 근거: 장흥 10분 6개 평균이 733.1733333 처럼 순환소수를 만들고,
    원본 제공자도 그 수준까지 적는다. DB 컬럼은 DOUBLE 이라 상한이 걸리지 않는다.
    """
    s = f"{x:.6f}".rstrip("0").rstrip(".")
    return s if s not in ("", "-") else "0"


def parse_ts(raw: str) -> datetime:
    """'2022-01-01 00:00'(장흥) / '2015-12-01 00'(화순) 양쪽을 받는다."""
    raw = raw.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d %H"):
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue
    raise ValueError(f"알 수 없는 시각 형식: {raw!r}")


def read_farm(path: Path, offset: int, expected_units: int, stats: dict):
    """원본 한 파일 → {(turbine_id, 정시): [(power, wind), ...]}.

    10분 표본은 같은 정시 버킷에 모인다. 정시 데이터만 있는 구간은 버킷당 1개가 된다.
    """
    buckets = defaultdict(list)
    seen_units = set()
    with path.open(encoding=ENCODING, errors="strict", newline="") as fh:
        reader = csv.reader(fh)
        header = next(reader)
        if len(header) < 5:
            raise SystemExit(f"{path.name}: 열이 {len(header)}개뿐이다 — 원본 형식이 아니다")
        for lineno, row in enumerate(reader, start=2):
            if not row or not row[TS].strip():
                continue
            unit = int(row[UNIT])
            seen_units.add(unit)
            hour = parse_ts(row[TS]).replace(minute=0, second=0, microsecond=0)

            pw = row[PW].strip()
            ws = row[WS].strip()
            if pw == "":
                stats["blank_power"] += 1
                continue  # 값이 없는 표본은 평균에서 뺀다(행을 지어내지 않는다)
            buckets[(unit + offset, hour)].append(
                (to_float(pw), to_float(ws) if ws != "" else None))
            stats["rows"] += 1
            if "," in pw:
                stats["comma_power"] += 1

    if len(seen_units) != expected_units:
        print(f"  ! 경고 {path.name}: 호기 {sorted(seen_units)} — {expected_units}기를 기대했다",
              file=sys.stderr)
    return buckets


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jangheung", required=True, type=Path)
    ap.add_argument("--hwasun", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--no-wind-speed", action="store_true",
                    help="풍속 열을 빼고 3열(turbine_id,recorded_at,power_output)로 낸다")
    ap.add_argument("--no-gap-fill", action="store_true",
                    help="결측 시각을 NULL 행으로 채우지 않는다(원본에 있는 행만 낸다)")
    args = ap.parse_args()

    merged = {}
    for label, path, key in (("장흥", args.jangheung, "jangheung"),
                             ("화순", args.hwasun, "hwasun")):
        offset, units = FARMS[key]
        stats = {"rows": 0, "blank_power": 0, "comma_power": 0}
        print(f"읽는 중 {label}: {path.name}")
        buckets = read_farm(path, offset, units, stats)
        print(f"  원본 {stats['rows']:,}행 · 빈 발전량 {stats['blank_power']:,}건"
              f" · 천단위 쉼표 {stats['comma_power']:,}건 → 정시 버킷 {len(buckets):,}개")
        collapsed = sum(1 for v in buckets.values() if len(v) > 1)
        print(f"  10분 표본이 접힌 버킷 {collapsed:,}개 (나머지는 원래 정시)")
        merged.update(buckets)

    # ── 결측 시각 격자 ────────────────────────────────────────────────────
    # 호기별 [최초, 최종] 안에서 빠진 정시를 빈 값 행으로 세운다. 값이 없다는 사실을
    # '행의 부재'가 아니라 'NULL 값'으로 표현해, 조회 API 가 sparse 배열을 내지 않게 한다.
    if not args.no_gap_fill:
        span = {}
        for tid, hour in merged:
            lo, hi = span.get(tid, (hour, hour))
            span[tid] = (min(lo, hour), max(hi, hour))
        added = 0
        for tid, (lo, hi) in sorted(span.items()):
            t, n = lo, 0
            while t <= hi:
                if (tid, t) not in merged:
                    merged[(tid, t)] = []      # 빈 표본 목록 = NULL 행
                    n += 1
                t += timedelta(hours=1)
            if n:
                print(f"  터빈 {tid:>2}: 결측 {n:,}시각을 NULL 행으로 채움"
                      f" ({lo:%Y-%m-%d} ~ {hi:%Y-%m-%d})")
            added += n
        print(f"격자 보정 합계 {added:,}행")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with gzip.open(args.out, "wt", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        for (tid, hour) in sorted(merged):
            samples = merged[(tid, hour)]
            if samples:
                power = sum(p for p, _ in samples) / len(samples)      # 시간 평균 kW
                winds = [s for _, s in samples if s is not None]
                wind = sum(winds) / len(winds) if winds else None
            else:
                power = wind = None                                     # 격자로 채운 결측 시각
            row = [tid, hour.strftime("%Y-%m-%d %H:%M:%S"),
                   "" if power is None else fmt(power)]
            if not args.no_wind_speed:
                row.append("" if wind is None else fmt(wind))
            w.writerow(row)
            written += 1

    size = args.out.stat().st_size
    print(f"\n완료: {args.out}  {written:,}행 · {size:,} bytes"
          f" · {'3열' if args.no_wind_speed else '4열(풍속 포함)'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
