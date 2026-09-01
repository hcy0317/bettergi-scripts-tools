package com.cloud_guest.artifact.nativeplan;

import java.util.List;

public record ArtifactNativeSyncPlan(
        ArtifactNativeSyncStatus status,
        boolean replaceLockPlans,
        boolean requiresPreMutationEvidence,
        int capacity,
        int sourceBuildCount,
        List<ArtifactNativeSetPlan> lockPlans,
        List<ArtifactNativeQuickEquipPlan> quickEquipPlans,
        List<ArtifactNativePlanIssue> issues,
        String planDigest,
        String translationMode,
        String message) {

    public ArtifactNativeSyncPlan {
        lockPlans = List.copyOf(lockPlans);
        quickEquipPlans = List.copyOf(quickEquipPlans);
        issues = List.copyOf(issues);
    }
}
