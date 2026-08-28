package com.cloud_guest.artifact.build;

import java.util.Optional;

public interface ArtifactBuildAutoActivationResultRepository {
    Optional<ArtifactBuildAutoActivationResult> find(String uid);
    ArtifactBuildAutoActivationResult save(String uid, ArtifactBuildAutoActivationResult result);
}
