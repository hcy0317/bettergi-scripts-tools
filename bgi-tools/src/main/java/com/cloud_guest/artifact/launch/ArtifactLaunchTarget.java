package com.cloud_guest.artifact.launch;

public record ArtifactLaunchTarget(
        int scanIndex,
        String expectedFingerprint,
        boolean expectedLocked) {
}
