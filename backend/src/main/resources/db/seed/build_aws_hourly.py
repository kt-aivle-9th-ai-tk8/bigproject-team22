"""기상청 AWS 시간자료 zip → aws_hourly.csv.gz 변환기 (aws_record 시드 소스 생성).

입력: 기상자료개방포털 '방재기상관측(AWS) 시간자료' zip 묶음이 든 디렉터리.
  - 바깥 zip 안에 연도별 zip(SURFACE_AWS_{지점}_HR_{연도}_*.zip), 그 안에 EUC-KR CSV 1개.
  - CSV 헤더: 지점,일시,기온(°C),풍향(deg),풍속(m/s),강수량(mm),현지기압(hPa),해면기압(hPa),습도(%),일사,일조

출력: aws_hourly.csv.gz — 헤더 없음, 컬럼은 aws_record 순서
  aws_station_id,recorded_at,temperature,pressure,humidity,wind_direction,precipitation

원칙:
  * 기압은 **현지기압**을 쓴다(해면기압 아님) — 밀도 보정(density_at_height)이 '관측지점 기압'을
    기준으로 허브고도까지 측고식 보정을 하므로, 해면으로 환원된 값을 넣으면 이중 보정이 된다.
  * 빈 값은 빈 칸으로 남긴다(적재 시 NULL). 0 이 아니라 '관측 없음'이며, 그 구분이
    기대발전량 NULL 전파(예측 불가 신호)의 근거다.
  * 같은 (지점,시각)이 여러 파일에 겹치면 나중 파일이 이긴다(연도 경계 중복 대비).

사용:
  python3 build_aws_hourly.py <zip들이 있는 디렉터리> [-o aws_hourly.csv.gz]
"""
import argparse
import csv
import gzip
import io
import sys
import zipfile
from pathlib import Path

HEADER_PREFIX = "지점,일시"
# 원본 헤더 인덱스: 0지점 1일시 2기온 3풍향 4풍속 5강수량 6현지기압 7해면기압 8습도 9일사 10일조
IDX = {"station": 0, "at": 1, "temperature": 2, "wind_direction": 3,
       "precipitation": 5, "pressure": 6, "humidity": 8}


def iter_csv_rows(path: Path):
    """바깥 zip → 연도 zip → CSV 행 순회. zip 이 아니면 CSV 로 직접 읽는다."""
    def rows_from_bytes(data: bytes, name: str):
        text = data.decode("euc-kr")
        for row in csv.reader(io.StringIO(text)):
            if not row or row[0].strip().startswith("지점"):
                continue
            yield row, name

    if path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as z:
            for info in sorted(z.infolist(), key=lambda i: i.filename):
                data = z.read(info.filename)
                if info.filename.lower().endswith(".zip"):
                    with zipfile.ZipFile(io.BytesIO(data)) as inner:
                        for member in sorted(inner.namelist()):
                            yield from rows_from_bytes(inner.read(member), member)
                elif info.filename.lower().endswith(".csv"):
                    yield from rows_from_bytes(data, info.filename)
    else:
        yield from rows_from_bytes(path.read_bytes(), path.name)


def norm_value(raw: str) -> str:
    """빈 값은 빈 칸(NULL), 숫자는 검증 후 그대로. '.9' 같은 표기도 float 로 통과된다."""
    v = raw.strip()
    if v == "":
        return ""
    float(v)  # 숫자가 아니면 여기서 즉시 실패 — 조용한 오적재 방지
    return v


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("src_dir", help="AWS zip 들이 있는 디렉터리")
    ap.add_argument("-o", "--out", default="aws_hourly.csv.gz")
    args = ap.parse_args()

    src = Path(args.src_dir)
    zips = sorted(src.glob("*.zip"))
    if not zips:
        print(f"zip 이 없다: {src}", file=sys.stderr)
        return 1

    merged = {}  # (station:int, at:str) -> row tuple. 나중 파일이 이긴다.
    for zp in zips:
        n = 0
        for row, name in iter_csv_rows(zp):
            station = int(row[IDX["station"]])
            at = row[IDX["at"]].strip() + ":00"  # 'YYYY-MM-DD HH:MM' → 초 보정
            merged[(station, at)] = (
                station, at,
                norm_value(row[IDX["temperature"]]),
                norm_value(row[IDX["pressure"]]),
                norm_value(row[IDX["humidity"]]),
                norm_value(row[IDX["wind_direction"]]),
                norm_value(row[IDX["precipitation"]]),
            )
            n += 1
        print(f"{zp.name}: {n:,}행")

    out = Path(args.out)
    with gzip.open(out, "wt", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        for key in sorted(merged):
            s, at, t, p, h, wd, pr = merged[key]
            w.writerow([s, at, t, p, h, wd, pr])

    stations = sorted({k[0] for k in merged})
    print(f"\n→ {out} : {len(merged):,}행, 지점 {stations}")
    for st in stations:
        ats = sorted(at for (s, at) in merged if s == st)
        missing_p = sum(1 for k in merged if k[0] == st and merged[k][3] == "")
        print(f"  지점 {st}: {ats[0]} ~ {ats[-1]}, 기압 결측 {missing_p:,}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
