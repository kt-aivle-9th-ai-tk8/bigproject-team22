# -*- coding: utf-8 -*-
"""
풍력 블레이드 결함 탐지 — SAHI + YOLO26 + EfficientNet-B3 심각도 분류

model.tar.gz 구조:
    yolo/best.pt
    effnet/best_efficientnet_b3_regularized_random_crop.pth

━━━━━━━━ 학습과 반드시 일치시켜야 하는 계약 ━━━━━━━━
  · 타일 1280×1280, overlap 0.2
  · conf 0.15  (YOLO26 F1 최적점)
  · perform_standard_pred=False
  · EfficientNet: ResizeShortSideIfNeeded(300) → CenterCrop(300) → Normalize
"""
from __future__ import annotations

import json
import os
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms
from torchvision.models import efficientnet_b3

TILE         = int(os.getenv("SAHI_TILE", "1280"))
OVERLAP      = float(os.getenv("SAHI_OVERLAP", "0.2"))
CONF         = float(os.getenv("DETECT_CONF", "0.15"))
MATCH_METRIC = os.getenv("SAHI_MATCH_METRIC", "IOS")
MATCH_THRESH = float(os.getenv("SAHI_MATCH_THRESH", "0.5"))
POSTPROCESS  = os.getenv("SAHI_POSTPROCESS", "GREEDYNMM")
TILE_BATCH   = int(os.getenv("TILE_BATCH", "8"))
DEVICE_ENV   = os.getenv("DETECT_DEVICE", "")

EFFNET_SIZE    = 300
EFFNET_DROPOUT = 0.4
SCHEMA_VERSION = "detect-1.0"


def _resolve_device(explicit: str = "") -> str:
    if explicit:
        return explicit
    try:
        if torch.cuda.is_available():
            return "cuda:0"
        if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
            return "mps"
    except Exception:
        pass
    return "cpu"


class _ResizeShortSide:
    def __init__(self, size: int = EFFNET_SIZE):
        self.size = size

    def __call__(self, img: Image.Image) -> Image.Image:
        img = img.convert("RGB")
        w, h = img.size
        if min(w, h) < self.size:
            scale = self.size / min(w, h)
            img = img.resize((max(self.size, int(round(w * scale))),
                              max(self.size, int(round(h * scale)))),
                             resample=Image.Resampling.BICUBIC)
        return img


_EFFNET_TRANSFORM = transforms.Compose([
    _ResizeShortSide(EFFNET_SIZE),
    transforms.CenterCrop(EFFNET_SIZE),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


def _build_effnet(num_classes: int) -> nn.Module:
    m = efficientnet_b3(weights=None)
    in_f = m.classifier[1].in_features
    m.classifier[0] = nn.Dropout(p=EFFNET_DROPOUT)
    m.classifier[1] = nn.Linear(in_f, num_classes)
    return m


def _classify_crop(crop_bgr: np.ndarray, effnet: nn.Module, device: str) -> int:
    import cv2
    rgb = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2RGB)
    t = _EFFNET_TRANSFORM(Image.fromarray(rgb)).unsqueeze(0).to(device)
    with torch.no_grad():
        return int(effnet(t).argmax(dim=1).item())


def model_fn(model_dir: str):
    device = _resolve_device(DEVICE_ENV)
    model_dir = Path(model_dir)

    from sahi import AutoDetectionModel
    yolo_path = model_dir / "yolo" / "best.pt"
    if not yolo_path.exists():
        cands = list(model_dir.glob("*.pt"))
        if not cands:
            raise FileNotFoundError(f"model_dir에 .pt가 없습니다: {model_dir}")
        yolo_path = cands[0]

    detector = AutoDetectionModel.from_pretrained(
        model_type="ultralytics",
        model_path=str(yolo_path),
        confidence_threshold=CONF,
        device=device,
        image_size=TILE,
    )

    effnet_path = model_dir / "effnet" / "best_efficientnet_b3_regularized_random_crop.pth"
    ckpt = torch.load(effnet_path, map_location="cpu", weights_only=False)
    class_names = ckpt["class_names"]
    effnet = _build_effnet(num_classes=len(class_names))
    effnet.load_state_dict(ckpt["model_state_dict"])
    effnet.eval().to(device)

    return {"detector": detector, "effnet": effnet, "severity_classes": class_names, "device": device}


def input_fn(request_body: bytes, content_type: str = "application/octet-stream") -> dict:
    import cv2
    if content_type in ("image/jpeg", "image/png", "application/octet-stream", "application/x-image"):
        arr = np.frombuffer(request_body if isinstance(request_body, (bytes, bytearray))
                            else request_body.read(), np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is None:
            raise ValueError("이미지 디코드 실패")
        return {"image": img, "image_id": None}
    if content_type == "application/json":
        import base64
        payload = json.loads(request_body)
        img = cv2.imdecode(np.frombuffer(base64.b64decode(payload["image_b64"]), np.uint8), cv2.IMREAD_COLOR)
        return {"image": img, "image_id": payload.get("image_id")}
    raise ValueError(f"지원하지 않는 content_type: {content_type}")


def predict_fn(data: dict, model: dict) -> dict:
    from sahi.predict import get_sliced_prediction

    image = data["image"]
    h, w = image.shape[:2]

    result = get_sliced_prediction(
        image, model["detector"],
        slice_height=TILE, slice_width=TILE,
        overlap_height_ratio=OVERLAP, overlap_width_ratio=OVERLAP,
        perform_standard_pred=False,
        postprocess_type=POSTPROCESS,
        postprocess_match_metric=MATCH_METRIC,
        postprocess_match_threshold=MATCH_THRESH,
        postprocess_class_agnostic=False,
        batch_size=TILE_BATCH,
        verbose=0,
    )

    defects = []
    for op in result.object_prediction_list:
        x1, y1, x2, y2 = op.bbox.minx, op.bbox.miny, op.bbox.maxx, op.bbox.maxy
        bw, bh = int(round(x2 - x1)), int(round(y2 - y1))
        if bw <= 0 or bh <= 0:
            continue
        x0c = max(0, int(round(x1))); y0c = max(0, int(round(y1)))
        crop = image[y0c:min(h, y0c+bh), x0c:min(w, x0c+bw)]
        severity_idx = _classify_crop(crop, model["effnet"], model["device"])
        defects.append({
            "class_id":   int(op.category.id),
            "class_name": op.category.name,
            "confidence": round(float(op.score.value), 4),
            "bbox": {"x": x0c, "y": y0c, "w": bw, "h": bh},
            "severity": model["severity_classes"][severity_idx],
        })

    defects.sort(key=lambda d: d["confidence"], reverse=True)
    return {
        "schema": SCHEMA_VERSION,
        "image_id": data.get("image_id"),
        "width": w, "height": h,
        "conf_threshold": CONF,
        "num_defects": len(defects),
        "defects": defects,
    }


def output_fn(prediction: dict, accept: str = "application/json") -> tuple[str, str]:
    return json.dumps(prediction, ensure_ascii=False), "application/json"


if __name__ == "__main__":
    import argparse, glob, time, shutil, tempfile, cv2
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--effnet", required=True)
    ap.add_argument("--image"); ap.add_argument("--image-dir")
    ap.add_argument("--out", default="detections.json")
    ap.add_argument("--device", default="")
    args = ap.parse_args()

    tmp = Path(tempfile.mkdtemp())
    (tmp / "yolo").mkdir(); (tmp / "effnet").mkdir()
    shutil.copy(args.weights, tmp / "yolo" / "best.pt")
    shutil.copy(args.effnet, tmp / "effnet" / "best_efficientnet_b3_regularized_random_crop.pth")
    if args.device:
        os.environ["DETECT_DEVICE"] = args.device

    mdl = model_fn(str(tmp))
    targets = ([args.image] if args.image else
               sorted(p for p in glob.glob(os.path.join(args.image_dir, "*"))
                      if p.lower().endswith((".jpg", ".jpeg", ".png"))))
    all_out = []
    for i, path in enumerate(targets, 1):
        img = cv2.imread(path)
        if img is None:
            continue
        t = time.time()
        res = predict_fn({"image": img, "image_id": os.path.basename(path)}, mdl)
        all_out.append(res)
        print(f"[{i}] {res['image_id']}: 결함 {res['num_defects']}건 ({time.time()-t:.1f}s)")
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(all_out, f, ensure_ascii=False, indent=2)
    shutil.rmtree(tmp, ignore_errors=True)