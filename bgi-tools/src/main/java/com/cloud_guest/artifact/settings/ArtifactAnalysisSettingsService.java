package com.cloud_guest.artifact.settings;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import org.springframework.stereotype.Service;

@Service
public class ArtifactAnalysisSettingsService {
    private final ArtifactAnalysisSettingsRepository repository;

    public ArtifactAnalysisSettingsService(ArtifactAnalysisSettingsRepository repository) {
        this.repository = repository;
    }

    public ArtifactAnalysisPolicy get() {
        return repository.get().orElseGet(ArtifactAnalysisPolicy::defaults);
    }

    public ArtifactAnalysisPolicy save(ArtifactAnalysisPolicy policy) {
        return repository.save(policy);
    }
}
