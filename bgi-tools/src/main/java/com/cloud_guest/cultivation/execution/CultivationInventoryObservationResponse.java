package com.cloud_guest.cultivation.execution;

public record CultivationInventoryObservationResponse(
        String status,
        String message,
        String uid,
        int revision,
        int observedCount
) {
}
