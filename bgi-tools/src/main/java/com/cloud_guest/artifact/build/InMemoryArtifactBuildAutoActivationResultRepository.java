package com.cloud_guest.artifact.build;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryArtifactBuildAutoActivationResultRepository
        implements ArtifactBuildAutoActivationResultRepository {
    private final Map<String, ArtifactBuildAutoActivationResult> results = new HashMap<>();

    @Override
    public Optional<ArtifactBuildAutoActivationResult> find(String uid) {
        return Optional.ofNullable(results.get(uid));
    }

    @Override
    public ArtifactBuildAutoActivationResult save(
            String uid,
            ArtifactBuildAutoActivationResult result) {
        results.put(uid, result);
        return result;
    }
}
