package com.cloud_guest.artifact.launch;

import java.util.List;

public record ArtifactLaunchRequest(
        int version,
        String kind,
        String uid,
        String jobId,
        ArtifactLaunchOperation operation,
        String createdAtUtc,
        String expiresAtUtc,
        Integer sourceArtifactCount,
        List<ArtifactLaunchTarget> targets,
        Integer nativeCapacity,
        String nativePlanDigest,
        Integer characterLevelThreshold,
        Boolean favoriteOverride,
        String gameNickname,
        String miliastraNickname) {

    public ArtifactLaunchRequest {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }
}
