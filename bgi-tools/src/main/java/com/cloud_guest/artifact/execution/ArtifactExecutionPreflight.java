package com.cloud_guest.artifact.execution;

import java.util.List;

public record ArtifactExecutionPreflight(
        ArtifactExecutionPreflightStatus status,
        List<ArtifactExecutionAction> actions,
        List<String> reasons) {

    public ArtifactExecutionPreflight {
        actions = List.copyOf(actions);
        reasons = List.copyOf(reasons);
    }
}
