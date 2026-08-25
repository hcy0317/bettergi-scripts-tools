package com.cloud_guest.cultivation.execution;

import java.util.Map;

public record CultivationActionResultRequest(
        String executorId,
        int expectedRevision,
        String idempotencyKey,
        boolean succeeded,
        Long observedOwned,
        Map<String, Integer> rewards,
        String terminationReason
) {
    public CultivationActionResultRequest {
        rewards = rewards == null ? Map.of() : Map.copyOf(rewards);
    }
}
