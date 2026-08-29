package com.cloud_guest.artifact.settings;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;

import java.util.Optional;

public interface ArtifactAnalysisSettingsRepository {
    Optional<ArtifactAnalysisPolicy> get();

    ArtifactAnalysisPolicy save(ArtifactAnalysisPolicy policy);
}
