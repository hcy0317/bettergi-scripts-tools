package com.cloud_guest.cultivation.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CultivationInventoryObservationRequest(
        String actionId,
        String executorId,
        int expectedRevision,
        String idempotencyKey,
        Map<String, Long> observedOwned
) {
    public CultivationInventoryObservationRequest {
        observedOwned = observedOwned == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(observedOwned));
    }
}
