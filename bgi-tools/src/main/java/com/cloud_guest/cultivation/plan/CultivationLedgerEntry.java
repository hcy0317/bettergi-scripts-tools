package com.cloud_guest.cultivation.plan;

import com.cloud_guest.cultivation.ocr.CultivationOcrBlock;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;

import java.util.List;

public record CultivationLedgerEntry(
        Integer sourceIndex,
        String materialName,
        long required,
        long baselineOwned,
        Long currentOwned,
        long remaining,
        RemainingEvidence remainingEvidence,
        Double ocrConfidence,
        boolean manuallyCorrected,
        List<CultivationOcrBlock> sourceBlocks
) {
    public CultivationLedgerEntry(Integer sourceIndex,
                                  String materialName,
                                  long required,
                                  long baselineOwned,
                                  long remaining,
                                  RemainingEvidence remainingEvidence,
                                  Double ocrConfidence,
                                  boolean manuallyCorrected,
                                  List<CultivationOcrBlock> sourceBlocks) {
        this(sourceIndex, materialName, required, baselineOwned, baselineOwned, remaining,
                remainingEvidence, ocrConfidence, manuallyCorrected, sourceBlocks);
    }

    public CultivationLedgerEntry {
        currentOwned = currentOwned == null ? baselineOwned : currentOwned;
        sourceBlocks = sourceBlocks == null ? List.of() : List.copyOf(sourceBlocks);
    }
}
