package com.cloud_guest.artifact.nativeplan;

import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;

public record ArtifactNativeSetPlan(
        String buildId,
        String buildName,
        String setKey,
        String slotKey,
        Set<String> mainStats,
        Set<String> substats) {

    public ArtifactNativeSetPlan {
        mainStats = Collections.unmodifiableSet(new LinkedHashSet<>(mainStats));
        substats = Collections.unmodifiableSet(new LinkedHashSet<>(substats));
    }
}
