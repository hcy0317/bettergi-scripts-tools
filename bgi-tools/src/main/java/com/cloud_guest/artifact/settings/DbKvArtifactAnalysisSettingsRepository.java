package com.cloud_guest.artifact.settings;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DbKvArtifactAnalysisSettingsRepository implements ArtifactAnalysisSettingsRepository {
    private static final String TYPE = "artifact-settings";
    private static final String KEY = "default";
    private final ArtifactJsonStore store;

    public DbKvArtifactAnalysisSettingsRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public Optional<ArtifactAnalysisPolicy> get() {
        return store.get(TYPE, KEY, ArtifactAnalysisPolicy.class);
    }

    @Override
    public ArtifactAnalysisPolicy save(ArtifactAnalysisPolicy policy) {
        return store.put(TYPE, KEY, policy);
    }
}
