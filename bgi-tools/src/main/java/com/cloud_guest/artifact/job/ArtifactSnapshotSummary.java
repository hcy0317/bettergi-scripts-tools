package com.cloud_guest.artifact.job;

public record ArtifactSnapshotSummary(
        int artifactCount,
        int analyzableArtifactCount,
        String snapshotDigest) {
}
