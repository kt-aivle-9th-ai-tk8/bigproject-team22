"""LightGBM 기대발전량 예측 — SageMaker Script mode 어댑터.

팀원이 준 predict.py / density.py는 한 글자도 수정하지 않는다.
predict.py의 HERE(모듈 전역변수)를 SageMaker가 실제로 모델을 풀어주는
model_dir로 런타임에 재지정해서, 코드(code/)와 모델(models/)을 S3에서
분리 배포해도 predict.py의 상대경로 가정이 그대로 맞아떨어지게 한다.

model_dir 안 기대 구조:
    model_dir/
    └── models/
        ├── hwasun/
        │   ├── manifest_v1.json
        │   ├── pooled_v1.txt
        │   └── U1_v1.txt ~ U8_v1.txt
        └── jangheung/
            ├── manifest_v1.json
            ├── pooled_v1.txt
            └── U1_v1.txt ~ U6_v1.txt
"""
import json
from pathlib import Path

import predict  # 팀원 원본, 수정 없음


def model_fn(model_dir):
    """기동 시 1회 — 발견되는 모든 farm의 모델을 미리 로드해 재사용한다."""
    predict.HERE = Path(model_dir)

    models_root = predict.HERE / "models"
    if not models_root.exists():
        raise FileNotFoundError(
            f"모델 디렉토리가 없습니다: {models_root}. "
            "model.tar.gz 안에 models/<farm>/... 구조가 있는지 확인하세요."
        )

    farms = [p.name for p in models_root.iterdir() if p.is_dir()]
    if not farms:
        raise FileNotFoundError(f"{models_root} 안에 farm 폴더가 하나도 없습니다.")

    return {farm: predict.FarmModels(farm) for farm in farms}


def input_fn(request_body, content_type):
    if content_type != "application/json":
        raise ValueError(
            f"지원하지 않는 content type입니다: {content_type} (application/json만 지원)"
        )
    data = json.loads(request_body)

    required = ["farm", "turbine_code"]
    missing = [k for k in required if k not in data]
    if missing:
        raise ValueError(f"필수 필드 누락: {missing}")

    return data


def predict_fn(input_data, models_by_farm):
    farm = input_data["farm"]
    models = models_by_farm.get(farm)
    if models is None:
        raise ValueError(
            f"알 수 없는 farm입니다: {farm} (사용 가능: {list(models_by_farm.keys())})"
        )

    return predict.expected_power(
        models,
        turbine_code=input_data["turbine_code"],
        wind_speed=input_data.get("wind_speed"),
        temperature=input_data.get("temperature"),
        pressure=input_data.get("pressure"),
        humidity=input_data.get("humidity"),
    )


def output_fn(prediction, accept):
    return json.dumps(prediction), "application/json"