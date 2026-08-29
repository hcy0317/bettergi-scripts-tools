package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.execution.ArtifactExecutionPreflight;

public record ArtifactJobPreflightResponse(
        ArtifactAnalysisJob job,
        ArtifactExecutionPreflight preflight) {
}
