package com.cloud_guest.artifact.analysis;

import java.util.List;

public record ArtifactAnalysisResult(
        String snapshotDigest,
        String policyVersion,
        String analysisInputDigest,
        List<String> buildIds,
        List<ArtifactDecision> decisions,
        ArtifactDecisionSummary summary) {

    public ArtifactAnalysisResult {
        analysisInputDigest = analysisInputDigest == null ? "" : analysisInputDigest;
        buildIds = buildIds == null ? List.of() : List.copyOf(buildIds);
        decisions = List.copyOf(decisions);
    }

    public ArtifactAnalysisResult(
            String snapshotDigest,
            String policyVersion,
            List<String> buildIds,
            List<ArtifactDecision> decisions,
            ArtifactDecisionSummary summary) {
        this(snapshotDigest, policyVersion, "", buildIds, decisions, summary);
    }

    public ArtifactAnalysisResult(
            String snapshotDigest,
            String policyVersion,
            List<ArtifactDecision> decisions,
            ArtifactDecisionSummary summary) {
        this(snapshotDigest, policyVersion, "", List.of(), decisions, summary);
    }
}
