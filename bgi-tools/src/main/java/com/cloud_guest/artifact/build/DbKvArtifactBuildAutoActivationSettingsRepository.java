package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DbKvArtifactBuildAutoActivationSettingsRepository
        implements ArtifactBuildAutoActivationSettingsRepository {
    private static final String TYPE = "artifact-build-auto-activation-settings";
    private static final String KEY = "default";
    private final ArtifactJsonStore store;

    public DbKvArtifactBuildAutoActivationSettingsRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public Optional<ArtifactBuildAutoActivationSettings> get() {
        return store.get(TYPE, KEY, ArtifactBuildAutoActivationSettings.class);
    }

    @Override
    public ArtifactBuildAutoActivationSettings save(ArtifactBuildAutoActivationSettings settings) {
        return store.put(TYPE, KEY, settings);
    }
}
