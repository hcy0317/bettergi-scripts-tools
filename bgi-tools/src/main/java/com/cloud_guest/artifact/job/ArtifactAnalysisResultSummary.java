package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.analysis.ArtifactDecisionSummary;

public record ArtifactAnalysisResultSummary(
        String policyVersion,
        String analysisInputDigest,
        ArtifactDecisionSummary summary) {
}
