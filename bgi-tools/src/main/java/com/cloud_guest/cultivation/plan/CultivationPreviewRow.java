package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.ocr.CultivationOcrBlock;
import com.cloud_guest.cultivation.ocr.CultivationRequirementRow;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;

import java.util.List;

public record CultivationPreviewRow(
        int sourceIndex,
        String materialName,
        long required,
        long observedOwned,
        long remaining,
        double confidence,
        boolean needsReview,
        RemainingEvidence remainingEvidence,
        List<CultivationOcrBlock> sourceBlocks
) {
    static CultivationPreviewRow from(int index, CultivationRequirementRow row) {
        return new CultivationPreviewRow(index, row.materialName(), row.required(), row.observedOwned(),
                row.remaining(), row.confidence(), row.needsReview(), row.remainingEvidence(), row.sourceBlocks());
    }
}
