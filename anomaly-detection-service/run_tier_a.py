"""매시간 배치: ① 미예측 scada_record에 LightGBM 예측/flags 채우기 ② 계층 A 판정."""
import sys
from datetime import datetime, timedelta

import db_client
from lightgbm.predict import FarmModels
from detection.tier_a_rules import detect

FARMS = ["jangheung", "hwasun"]
LOOKBACK_DAYS = 7  # 정지·부재 판정에 필요한 최근 구간만


def main():
    since = datetime.utcnow() - timedelta(days=LOOKBACK_DAYS)
    total_predicted, total_events = 0, 0

    for farm_code in FARMS:
        models = FarmModels(farm_code)  # lightgbm/models/<farm>/manifest_v1.json 로드

        predicted = db_client.run_predictions(farm_code, models)
        print(f"[{farm_code}] 예측 채움 {predicted}건")
        total_predicted += predicted

        scored = db_client.load_scada_for_detection(farm_code, since=since)
        if scored.empty:
            print(f"[{farm_code}] 판정 대상 없음, 스킵")
            continue

        events = detect(scored, farm_code)
        n = db_client.save_anomaly_events(events, farm_code)
        print(f"[{farm_code}] 계층 A 이벤트 {len(events)}건 판정, {n}건 저장")
        total_events += n

    print(f"완료 — 예측 {total_predicted}건, 이벤트 {total_events}건 저장")


if __name__ == "__main__":
    sys.exit(main())