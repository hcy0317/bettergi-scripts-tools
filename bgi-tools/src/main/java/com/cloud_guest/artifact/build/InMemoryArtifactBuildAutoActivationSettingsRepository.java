package com.cloud_guest.artifact.build;

import java.util.Optional;

public class InMemoryArtifactBuildAutoActivationSettingsRepository
        implements ArtifactBuildAutoActivationSettingsRepository {
    private ArtifactBuildAutoActivationSettings settings;

    @Override
    public Optional<ArtifactBuildAutoActivationSettings> get() {
        return Optional.ofNullable(settings);
    }

    @Override
    public ArtifactBuildAutoActivationSettings save(ArtifactBuildAutoActivationSettings settings) {
        this.settings = settings;
        return settings;
    }
}
