package com.cloud_guest.cultivation.ocr;

import java.util.List;

public record CultivationOcrResult(
        String engineVersion,
        String modelSource,
        int imageWidth,
        int imageHeight,
        List<CultivationOcrBlock> blocks
) {
    public CultivationOcrResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
