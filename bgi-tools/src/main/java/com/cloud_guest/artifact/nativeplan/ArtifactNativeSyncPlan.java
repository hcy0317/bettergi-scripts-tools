package com.cloud_guest.artifact.nativeplan;

import java.util.List;

public record ArtifactNativeSyncPlan(
        ArtifactNativeSyncStatus status,
        boolean replaceAll,
        boolean requiresPreMutationEvidence,
        int capacity,
        int sourceBuildCount,
        List<ArtifactNativeSetPlan> plans,
        String planDigest,
        String translationMode,
        String message) {

    public ArtifactNativeSyncPlan {
        plans = List.copyOf(plans);
    }
}
