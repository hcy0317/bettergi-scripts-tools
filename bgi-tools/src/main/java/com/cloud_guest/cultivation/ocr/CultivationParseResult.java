package com.cloud_guest.cultivation.ocr;

import java.util.List;

public record CultivationParseResult(
        List<CultivationRequirementRow> requirements,
        List<String> warnings
) {
    public CultivationParseResult {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
