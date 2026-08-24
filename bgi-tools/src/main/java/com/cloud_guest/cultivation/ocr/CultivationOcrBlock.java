package com.cloud_guest.cultivation.ocr;

import java.util.List;

public record CultivationOcrBlock(String text, double confidence, List<OcrPoint> polygon) {

    public CultivationOcrBlock {
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }

    public double left() {
        return polygon.stream().mapToDouble(OcrPoint::x).min().orElse(0);
    }

    public double right() {
        return polygon.stream().mapToDouble(OcrPoint::x).max().orElse(0);
    }

    public double top() {
        return polygon.stream().mapToDouble(OcrPoint::y).min().orElse(0);
    }

    public double bottom() {
        return polygon.stream().mapToDouble(OcrPoint::y).max().orElse(0);
    }

    public double centerX() {
        return (left() + right()) / 2.0;
    }

    public double centerY() {
        return (top() + bottom()) / 2.0;
    }

    public double height() {
        return bottom() - top();
    }
}
