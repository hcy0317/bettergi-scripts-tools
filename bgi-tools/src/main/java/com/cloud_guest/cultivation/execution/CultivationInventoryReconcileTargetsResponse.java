package com.cloud_guest.cultivation.execution;

import java.time.LocalDateTime;
import java.util.List;

public record CultivationInventoryReconcileTargetsResponse(
        String status,
        String message,
        String uid,
        int revision,
        String actionId,
        LocalDateTime leaseExpiresAt,
        List<String> materialNames
) {
    public CultivationInventoryReconcileTargetsResponse {
        materialNames = materialNames == null ? List.of() : List.copyOf(materialNames);
    }
}
