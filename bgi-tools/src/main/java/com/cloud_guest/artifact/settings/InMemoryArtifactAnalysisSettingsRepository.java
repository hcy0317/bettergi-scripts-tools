package com.cloud_guest.artifact.settings;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;

import java.util.Optional;

public class InMemoryArtifactAnalysisSettingsRepository implements ArtifactAnalysisSettingsRepository {
    private ArtifactAnalysisPolicy policy;

    @Override
    public Optional<ArtifactAnalysisPolicy> get() {
        return Optional.ofNullable(policy);
    }

    @Override
    public ArtifactAnalysisPolicy save(ArtifactAnalysisPolicy policy) {
        this.policy = policy;
        return policy;
    }
}
