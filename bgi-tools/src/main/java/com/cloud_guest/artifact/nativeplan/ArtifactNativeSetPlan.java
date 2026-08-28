package com.cloud_guest.artifact.nativeplan;

import java.util.Set;

public record ArtifactNativeSetPlan(
        String setKey,
        String slotKey,
        Set<String> mainStats,
        Set<String> substats) {

    public ArtifactNativeSetPlan {
        mainStats = Set.copyOf(mainStats);
        substats = Set.copyOf(substats);
    }
}
