"""매일 배치: 계층 B(만성 저하) 판정. 예측은 run_tier_a.py가 매시간 이미 채워둔 상태를 전제."""
import sys

import db_client
from detection.tier_b_batch import detect

FARMS = ["jangheung", "hwasun"]


def main():
    total_events = 0

    for farm_code in FARMS:
        # SE_noise 추정(학습구간 ≤2023)과 30일 롤링 모두 필요 → 전체 이력 조회
        scored = db_client.load_scada_for_detection(farm_code, since=None)
        if scored.empty:
            print(f"[{farm_code}] 데이터 없음, 스킵")
            continue

        events = detect(scored, farm_code)
        se_noise = events.attrs.get("se_noise")
        n = db_client.save_anomaly_events(events, farm_code)
        print(f"[{farm_code}] SE_noise={se_noise:.4f} 계층 B 이벤트 {len(events)}건 판정, {n}건 저장")
        total_events += n

    print(f"완료 — 총 {total_events}건 저장")


if __name__ == "__main__":
    sys.exit(main())