package com.cloud_guest.artifact.build;

import java.util.Optional;

public interface ArtifactBuildAutoActivationSettingsRepository {
    Optional<ArtifactBuildAutoActivationSettings> get();

    ArtifactBuildAutoActivationSettings save(ArtifactBuildAutoActivationSettings settings);
}
