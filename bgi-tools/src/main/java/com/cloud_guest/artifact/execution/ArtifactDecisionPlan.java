package com.cloud_guest.artifact.execution;

import com.cloud_guest.artifact.analysis.ArtifactDecision;

import java.util.List;

public record ArtifactDecisionPlan(
        String planId,
        String uid,
        int sourceArtifactCount,
        String sourceSnapshotDigest,
        boolean approved,
        List<ArtifactDecision> decisions) {

    public ArtifactDecisionPlan {
        decisions = List.copyOf(decisions);
    }
}
