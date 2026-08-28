package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.launch.ArtifactLaunchResult;

public record ArtifactJobStartResponse(ArtifactAnalysisJob job, ArtifactLaunchResult launch) {
}
