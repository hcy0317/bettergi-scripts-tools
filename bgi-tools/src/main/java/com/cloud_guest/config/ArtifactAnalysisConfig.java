package com.cloud_guest.config;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisEngine;
import com.cloud_guest.artifact.execution.ArtifactExecutionGuard;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobRepository;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobService;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.nativeplan.ArtifactNativePlanCompiler;
import com.cloud_guest.cultivation.execution.CultivationMaterialSourceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ArtifactAnalysisConfig {
    @Bean
    public ArtifactAnalysisEngine artifactAnalysisEngine() {
        return new ArtifactAnalysisEngine();
    }

    @Bean
    public ArtifactExecutionGuard artifactExecutionGuard() {
        return new ArtifactExecutionGuard();
    }

    @Bean
    public ArtifactNativePlanCompiler artifactNativePlanCompiler() {
        return new ArtifactNativePlanCompiler();
    }

    @Bean
    public ArtifactLaunchRequestService artifactLaunchRequestService(
            CultivationMaterialSourceCatalog materialSourceCatalog,
            ObjectMapper objectMapper) {
        return new ArtifactLaunchRequestService(
                materialSourceCatalog::betterGiRoot,
                objectMapper,
                Clock.systemUTC(),
                Duration.ofMinutes(5));
    }

    @Bean
    public ArtifactAnalysisJobService artifactAnalysisJobService(
            ArtifactAnalysisJobRepository repository,
            ArtifactAnalysisEngine analysisEngine,
            ArtifactExecutionGuard executionGuard,
            ArtifactLaunchRequestService launchRequestService) {
        return new ArtifactAnalysisJobService(
                repository, analysisEngine, executionGuard, launchRequestService, Clock.systemUTC());
    }
}
