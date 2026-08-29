package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;

public record ArtifactHostCompletion(
        ArtifactLaunchOperation operation,
        boolean success,
        String message) {
}
