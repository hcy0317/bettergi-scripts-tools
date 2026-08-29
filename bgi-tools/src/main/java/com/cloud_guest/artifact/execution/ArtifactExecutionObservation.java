package com.cloud_guest.artifact.execution;

import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;

import java.util.List;

public record ArtifactExecutionObservation(
        String uid,
        int artifactCount,
        List<ArtifactItem> artifacts,
        ArtifactSnapshot fullSnapshot,
        boolean countOnly) {

    public ArtifactExecutionObservation(
            String uid,
            int artifactCount,
            List<ArtifactItem> artifacts,
            ArtifactSnapshot fullSnapshot) {
        this(uid, artifactCount, artifacts, fullSnapshot, false);
    }

    public ArtifactExecutionObservation {
        if (uid == null || !uid.matches("[0-9]{6,12}")) {
            throw new IllegalArgumentException("valid uid is required");
        }
        if (artifactCount < 0) throw new IllegalArgumentException("artifact count must be nonnegative");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (countOnly && (!artifacts.isEmpty() || fullSnapshot != null)) {
            throw new IllegalArgumentException("count-only observation cannot include artifact details");
        }
        if (fullSnapshot != null
                && (!uid.equals(fullSnapshot.uid()) || artifactCount != fullSnapshot.artifactCount())) {
            throw new IllegalArgumentException("full snapshot does not match execution observation");
        }
    }
}
