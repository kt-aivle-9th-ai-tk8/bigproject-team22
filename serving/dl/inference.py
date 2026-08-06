"""
블레이드 결함 탐지(YOLOv11) + 심각도 분류(EfficientNet-B3) — SageMaker Async Inference 어댑터

* 한 컨테이너 안에서 두 모델을 순차 처리한다 (SageMaker Async Inference는 멀티 컨테이너 Serial Pipeline을 지원하지 않으므로 이 구조가 필요하다):
    이미지 → YOLOv11 결함 탐지 → 박스별 크롭 → EfficientNet-B3 심각도 분류

* model_dir 안 기대 구조:
    model_dir/
    ├── yolov11.pt                                        # ultralytics 학습 가중치
    ├── best_efficientnet_b3_regularized_random_crop.pth  # 팀원 체크포인트 (dict, class_names 내장)
    └── postprocess_config.json                            

* 주의:
    - 결함 클래스명(defect_type)은 YOLO 모델 자체(model.names)에서 가져온다.
    - EfficientNet의 심각도 클래스명(severity)도 체크포인트의 class_names에서 가져온다.
    - 즉 별도 클래스 매핑 JSON(defect_classes.json / severity_classes.json)이 필요 없다
"""
import io
import json
from pathlib import Path

import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms
from torchvision.models import efficientnet_b3
from ultralytics import YOLO

DEFAULT_POSTPROCESS_CONFIG = {
    "confidence_threshold": 0.4,
    "iou_threshold": 0.5,
    "crop_padding_ratio": 0.1,  # 박스 각 변에 10%씩 여유를 두고 크롭 (분류 정확도 향상)
}

EFFNET_IMAGE_SIZE = 300
EFFNET_DROPOUT = 0.4  # 학습 스크립트와 동일하게 맞춰야 state_dict 로딩이 성공함


class ResizeShortSideIfNeeded:
    """짧은 변이 target_size보다 작을 때만 종횡비를 유지하며 확대한다.

    train_efficientnet_b3_regularized_random_crop.py의 검증(valid) 전처리와
    동일한 클래스를 그대로 옮겨왔다 — 학습 때와 추론 때 전처리가 어긋나면
    안 되므로 원본 그대로 복제해서 쓴다.
    """

    def __init__(self, target_size: int = EFFNET_IMAGE_SIZE):
        self.target_size = target_size

    def __call__(self, image: Image.Image) -> Image.Image:
        image = image.convert("RGB")
        width, height = image.size
        target = self.target_size
        short_side = min(width, height)

        if short_side < target:
            scale = target / short_side
            new_width = max(target, int(round(width * scale)))
            new_height = max(target, int(round(height * scale)))
            image = image.resize((new_width, new_height), resample=Image.Resampling.BICUBIC)

        return image


EFFNET_TRANSFORM = transforms.Compose([
    ResizeShortSideIfNeeded(EFFNET_IMAGE_SIZE),
    transforms.CenterCrop(EFFNET_IMAGE_SIZE),  # 검증 때와 동일 — RandomCrop 아님
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


def _build_efficientnet(num_classes: int) -> nn.Module:
    """학습 스크립트와 동일한 구조로 재구성 — 안 맞추면 load_state_dict가 실패한다."""
    model = efficientnet_b3(weights=None)
    in_features = model.classifier[1].in_features
    model.classifier[0] = nn.Dropout(p=EFFNET_DROPOUT)
    model.classifier[1] = nn.Linear(in_features, num_classes)
    return model


def model_fn(model_dir):
    model_dir = Path(model_dir)

    yolo_model = YOLO(str(model_dir / "yolov11.pt"))

    checkpoint = torch.load(
        model_dir / "best_efficientnet_b3_regularized_random_crop.pth",
        map_location="cpu",
        weights_only=False,  # 체크포인트가 class_names 등 메타데이터를 함께 담고 있어서 필요
    )
    class_names = checkpoint["class_names"]
    effnet_model = _build_efficientnet(num_classes=len(class_names))
    effnet_model.load_state_dict(checkpoint["model_state_dict"])
    effnet_model.eval()

    config_path = model_dir / "postprocess_config.json"
    postprocess_config = DEFAULT_POSTPROCESS_CONFIG.copy()
    if config_path.exists():
        postprocess_config.update(json.loads(config_path.read_text(encoding="utf-8")))

    return {
        "yolo": yolo_model,
        "effnet": effnet_model,
        "severity_classes": class_names,
        "postprocess_config": postprocess_config,
    }


def input_fn(request_body, content_type):
    if content_type not in ("image/jpeg", "image/png", "application/octet-stream"):
        raise ValueError(f"지원하지 않는 content type입니다: {content_type}")
    return Image.open(io.BytesIO(request_body)).convert("RGB")


def _crop_with_padding(image: Image.Image, bbox, padding_ratio: float) -> Image.Image:
    x1, y1, x2, y2 = bbox
    w, h = x2 - x1, y2 - y1
    pad_x, pad_y = w * padding_ratio, h * padding_ratio

    img_w, img_h = image.size
    left = max(0, int(x1 - pad_x))
    top = max(0, int(y1 - pad_y))
    right = min(img_w, int(x2 + pad_x))
    bottom = min(img_h, int(y2 + pad_y))

    return image.crop((left, top, right, bottom))


def predict_fn(image: Image.Image, model):
    yolo_model = model["yolo"]
    effnet_model = model["effnet"]
    severity_classes = model["severity_classes"]
    config = model["postprocess_config"]

    # 1. YOLOv11 결함 탐지 (NMS는 ultralytics 내부에서 conf/iou 기준으로 처리됨)
    results = yolo_model.predict(
        image,
        conf=config["confidence_threshold"],
        iou=config["iou_threshold"],
        verbose=False,
    )[0]

    defects = []
    for box in results.boxes:
        bbox = box.xyxy[0].tolist()  # [x1, y1, x2, y2]
        confidence = float(box.conf[0])
        class_id = int(box.cls[0])
        defect_type = yolo_model.names[class_id]  # YOLO 모델 자체에 내장된 클래스명

        # 2. 박스 크롭 → EfficientNet-B3 심각도 분류
        crop = _crop_with_padding(image, bbox, config["crop_padding_ratio"])
        crop_tensor = EFFNET_TRANSFORM(crop).unsqueeze(0)

        with torch.no_grad():
            logits = effnet_model(crop_tensor)
            severity_idx = logits.argmax(dim=1).item()

        defects.append({
            "bbox": [round(v, 1) for v in bbox],
            "defect_type": defect_type,
            "confidence": round(confidence, 4),
            "severity": severity_classes[severity_idx],
        })

    return defects


def output_fn(prediction, accept):
    return json.dumps(prediction), "application/json"