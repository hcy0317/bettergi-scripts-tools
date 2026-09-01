package com.cloud_guest.artifact.nativeplan;

import java.util.List;

public record ArtifactNativePlanIssue(
        String code,
        String subjectKey,
        List<String> buildIds,
        String message) {

    public ArtifactNativePlanIssue {
        buildIds = List.copyOf(buildIds);
    }
}
