package com.cloud_guest.cultivation.ocr;

import java.util.List;

public record CultivationRequirementRow(
        String materialName,
        long required,
        long remaining,
        double confidence,
        boolean needsReview,
        RemainingEvidence remainingEvidence,
        List<CultivationOcrBlock> sourceBlocks
) {
    public CultivationRequirementRow {
        sourceBlocks = sourceBlocks == null ? List.of() : List.copyOf(sourceBlocks);
    }

    public long observedOwned() {
        return Math.max(required - remaining, 0);
    }
}
