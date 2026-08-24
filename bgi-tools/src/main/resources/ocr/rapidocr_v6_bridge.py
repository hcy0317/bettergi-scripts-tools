from __future__ import annotations

import argparse
import contextlib
import importlib.metadata
import json
import os
import sys
import tempfile
import traceback
from pathlib import Path

import cv2
import numpy as np
import yaml
from rapidocr import RapidOCR
from rapidocr.utils.typings import OCRVersion


DET_RELATIVE = Path(
    "Assets/Model/PaddleOCR/Det/V6/PP-OCRv6_small_det_infer/slim.onnx"
)
REC_RELATIVE = Path(
    "Assets/Model/PaddleOCR/Rec/V6/PP-OCRv6_small_rec_infer/slim.onnx"
)
REC_CONFIG_RELATIVE = REC_RELATIVE.with_name("inference.yml")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--bettergi-root", type=Path)
    parser.add_argument("--tile-height", type=int, default=3500)
    parser.add_argument("--tile-overlap", type=int, default=150)
    return parser.parse_args()


def build_engine(bettergi_root: Path | None):
    params = {
        "Global.max_side_len": 8192,
        "Global.use_cls": False,
        "Det.limit_side_len": 2048,
        "Det.ocr_version": OCRVersion.PPOCRV6,
        "Rec.ocr_version": OCRVersion.PPOCRV6,
    }
    dictionary_path = None
    model_source = "rapidocr-default-assets"

    if bettergi_root:
        det_path = bettergi_root / DET_RELATIVE
        rec_path = bettergi_root / REC_RELATIVE
        config_path = bettergi_root / REC_CONFIG_RELATIVE
        if det_path.is_file() and rec_path.is_file() and config_path.is_file():
            config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
            characters = config["PostProcess"]["character_dict"]
            dictionary = tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", suffix=".txt", delete=False
            )
            dictionary.writelines(f"{character}\n" for character in characters)
            dictionary.close()
            dictionary_path = Path(dictionary.name)
            params.update(
                {
                    "Det.model_path": det_path,
                    "Rec.model_path": rec_path,
                    "Rec.rec_keys_path": dictionary_path,
                }
            )
            model_source = "bettergi-installed-assets"

    with contextlib.redirect_stdout(sys.stderr):
        engine = RapidOCR(params=params)
    return engine, model_source, dictionary_path


def load_image(path: Path) -> np.ndarray:
    encoded = np.fromfile(path, dtype=np.uint8)
    image = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Cannot decode image: {path}")
    return image


def normalize_text(text: str) -> str:
    return "".join(text.split())


def make_block(text, score, polygon, y_offset):
    points = [
        {"x": float(point[0]), "y": float(point[1]) + y_offset}
        for point in polygon
    ]
    return {
        "text": str(text),
        "confidence": float(score),
        "polygon": points,
    }


def bounds(block):
    xs = [point["x"] for point in block["polygon"]]
    ys = [point["y"] for point in block["polygon"]]
    return min(xs), min(ys), max(xs), max(ys)


def is_duplicate(first, second):
    if normalize_text(first["text"]) != normalize_text(second["text"]):
        return False
    left_a, top_a, right_a, bottom_a = bounds(first)
    left_b, top_b, right_b, bottom_b = bounds(second)
    center_a = ((left_a + right_a) / 2, (top_a + bottom_a) / 2)
    center_b = ((left_b + right_b) / 2, (top_b + bottom_b) / 2)
    height = max(bottom_a - top_a, bottom_b - top_b, 12)
    width = max(right_a - left_a, right_b - left_b, 20)
    return (
        abs(center_a[0] - center_b[0]) <= max(16, width * 0.2)
        and abs(center_a[1] - center_b[1]) <= max(12, height * 0.7)
    )


def add_deduplicated(blocks, candidate):
    for index, existing in enumerate(blocks):
        if is_duplicate(existing, candidate):
            if candidate["confidence"] > existing["confidence"]:
                blocks[index] = candidate
            return
    blocks.append(candidate)


def recognize(engine, image, tile_height, tile_overlap):
    height = image.shape[0]
    step = max(1, tile_height - tile_overlap)
    blocks = []
    top = 0
    while top < height:
        bottom = min(height, top + tile_height)
        with contextlib.redirect_stdout(sys.stderr):
            result = engine(image[top:bottom])
        texts = result.txts if result and result.txts is not None else []
        scores = result.scores if result and result.scores is not None else []
        polygons = result.boxes if result and result.boxes is not None else []
        found_boundary = False
        for text, score, polygon in zip(texts, scores, polygons):
            block = make_block(text, score, polygon, top)
            add_deduplicated(blocks, block)
            if "背包内" in normalize_text(str(text)):
                found_boundary = True
        if bottom >= height or found_boundary:
            break
        top += step
    blocks.sort(key=lambda block: (bounds(block)[1], bounds(block)[0]))
    return blocks


def main() -> int:
    args = parse_args()
    dictionary_path = None
    try:
        image = load_image(args.input)
        engine, model_source, dictionary_path = build_engine(args.bettergi_root)
        blocks = recognize(engine, image, args.tile_height, args.tile_overlap)
        payload = {
            "engineVersion": (
                f"RapidOCR {importlib.metadata.version('rapidocr')} / PP-OCRv6"
            ),
            "modelSource": model_source,
            "imageWidth": int(image.shape[1]),
            "imageHeight": int(image.shape[0]),
            "blocks": blocks,
        }
        print(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception:
        traceback.print_exc(file=sys.stderr)
        return 2
    finally:
        if dictionary_path:
            try:
                os.unlink(dictionary_path)
            except OSError:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
