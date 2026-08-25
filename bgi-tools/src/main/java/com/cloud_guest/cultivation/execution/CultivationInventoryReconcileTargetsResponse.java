package com.cloud_guest.cultivation.execution;

import java.util.List;

public record CultivationInventoryReconcileTargetsResponse(
        String uid,
        int revision,
        List<String> materialNames
) {
    public CultivationInventoryReconcileTargetsResponse {
        materialNames = materialNames == null ? List.of() : List.copyOf(materialNames);
    }
}
