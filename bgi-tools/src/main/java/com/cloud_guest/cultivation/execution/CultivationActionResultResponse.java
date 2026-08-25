package com.cloud_guest.cultivation.execution;

public record CultivationActionResultResponse(
        String status,
        String message,
        String uid,
        String actionId,
        int revision,
        String materialName,
        Long observedOwned
) {
}
