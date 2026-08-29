package com.cloud_guest.artifact.build;

import org.springframework.stereotype.Service;

@Service
public class ArtifactBuildAutoActivationSettingsService {
    private final ArtifactBuildAutoActivationSettingsRepository repository;

    public ArtifactBuildAutoActivationSettingsService(
            ArtifactBuildAutoActivationSettingsRepository repository) {
        this.repository = repository;
    }

    public ArtifactBuildAutoActivationSettings get() {
        return repository.get().orElseGet(ArtifactBuildAutoActivationSettings::defaults);
    }

    public ArtifactBuildAutoActivationSettings save(ArtifactBuildAutoActivationSettings settings) {
        return repository.save(settings);
    }
}
