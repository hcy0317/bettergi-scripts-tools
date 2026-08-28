package com.cloud_guest.artifact.build;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("databaseInitRunner")
public class ArtifactPresetInitializer {
    private final ArtifactBuildService buildService;
    private final ArtifactPresetCatalog catalog;

    public ArtifactPresetInitializer(ArtifactBuildService buildService, ArtifactPresetCatalog catalog) {
        this.buildService = buildService;
        this.catalog = catalog;
    }

    @PostConstruct
    public void initialize() {
        buildService.synchronizePresets(catalog.builds());
    }
}
