package com.cloud_guest.cultivation.execution;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CultivationInventoryReconcileTargetsResponse(
        String status,
        String message,
        String uid,
        int revision,
        String actionId,
        LocalDateTime leaseExpiresAt,
        List<String> materialNames,
        Map<String, List<String>> materialNamesByGrid
) {
    public CultivationInventoryReconcileTargetsResponse {
        materialNames = materialNames == null ? List.of() : List.copyOf(materialNames);
        materialNamesByGrid = materialNamesByGrid == null ? Map.of() : materialNamesByGrid.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }

    public CultivationInventoryReconcileTargetsResponse(
            String status,
            String message,
            String uid,
            int revision,
            String actionId,
            LocalDateTime leaseExpiresAt,
            List<String> materialNames) {
        this(status, message, uid, revision, actionId, leaseExpiresAt, materialNames, Map.of());
    }
}
