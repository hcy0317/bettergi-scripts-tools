package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DbKvArtifactBuildAutoActivationResultRepository
        implements ArtifactBuildAutoActivationResultRepository {
    private static final String TYPE = "artifact-build-auto-activation-result";
    private final ArtifactJsonStore store;

    public DbKvArtifactBuildAutoActivationResultRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public Optional<ArtifactBuildAutoActivationResult> find(String uid) {
        return store.get(TYPE, uid, ArtifactBuildAutoActivationResult.class);
    }

    @Override
    public ArtifactBuildAutoActivationResult save(
            String uid,
            ArtifactBuildAutoActivationResult result) {
        return store.put(TYPE, uid, result);
    }
}
