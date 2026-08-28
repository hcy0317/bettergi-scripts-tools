package com.cloud_guest.artifact.analysis;

public record ArtifactDecisionSummary(int keep, int reject, int unscored, int lock, int unlock, int unchanged) {
}
