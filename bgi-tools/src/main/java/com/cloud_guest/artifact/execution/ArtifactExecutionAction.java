package com.cloud_guest.artifact.execution;

public record ArtifactExecutionAction(
        int scanIndex,
        boolean expectedLocked,
        boolean desiredLocked,
        String expectedFingerprint) {
}
