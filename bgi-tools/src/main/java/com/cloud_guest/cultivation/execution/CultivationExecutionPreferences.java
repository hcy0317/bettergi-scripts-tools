package com.cloud_guest.cultivation.execution;

public record CultivationExecutionPreferences(
        String uid,
        String domainParty,
        String gatherParty,
        String gatherFallbackParty,
        boolean gatherEnabled
) {
}
