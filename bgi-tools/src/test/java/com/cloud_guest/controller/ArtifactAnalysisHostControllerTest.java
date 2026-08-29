package com.cloud_guest.controller;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisEngine;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationService;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.cloud_guest.artifact.build.ArtifactBuildService;
import com.cloud_guest.artifact.build.InMemoryArtifactBuildRepository;
import com.cloud_guest.artifact.build.InMemoryArtifactBuildAutoActivationResultRepository;
import com.cloud_guest.artifact.character.ArtifactCharacterRoster;
import com.cloud_guest.artifact.character.ArtifactCharacterRosterEntry;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.execution.ArtifactExecutionGuard;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobService;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobStatus;
import com.cloud_guest.artifact.job.ArtifactHostCompletion;
import com.cloud_guest.artifact.job.InMemoryArtifactAnalysisJobRepository;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.nativeplan.ArtifactNativePlanCompiler;
import com.cloud_guest.artifact.settings.ArtifactAnalysisSettingsService;
import com.cloud_guest.artifact.settings.InMemoryArtifactAnalysisSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactAnalysisHostControllerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void claimedRosterAppliesBoundSettingsBeforeSuccessfulCompletion() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
        ArtifactLaunchRequestService launchService = new ArtifactLaunchRequestService(
                tempDirectory, new ObjectMapper(), clock, Duration.ofMinutes(5));
        ArtifactBuildService buildService = new ArtifactBuildService(
                new InMemoryArtifactBuildRepository());
        buildService.importAll(List.of(build("furina", "Furina"), build("noelle", "Noelle")));
        ArtifactAnalysisJobService jobService = new ArtifactAnalysisJobService(
                new InMemoryArtifactAnalysisJobRepository(), new ArtifactAnalysisEngine(),
                new ArtifactExecutionGuard(), launchService, clock);
        ArtifactBuildAutoActivationService autoActivationService =
                new ArtifactBuildAutoActivationService(
                        buildService,
                        new InMemoryArtifactBuildAutoActivationResultRepository());
        ArtifactAnalysisHostController controller = new ArtifactAnalysisHostController(
                launchService, jobService, buildService,
                autoActivationService,
                new ArtifactAnalysisSettingsService(new InMemoryArtifactAnalysisSettingsRepository()),
                new ArtifactNativePlanCompiler());
        var start = jobService.startCharacterRoster(
                "102550550", new ArtifactBuildAutoActivationSettings(80, true),
                "眇", "遥", "MannequinGirl");

        controller.claim(
                start.job().id(), start.job().uid(), ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER,
                start.launch().requestToken());
        var result = controller.submitCharacterRoster(
                start.job().id(), start.launch().requestToken(),
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Furina", 90, false),
                        new ArtifactCharacterRosterEntry("Noelle", 80, false)))).getData();
        var completed = controller.complete(
                start.job().id(), start.launch().requestToken(),
                new ArtifactHostCompletion(
                        ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER, true, null)).getData();

        assertThat(result.favoriteCharacterCount()).isZero();
        assertThat(result.levelEligibleCharacterCount()).isEqualTo(2);
        assertThat(result.eligibleCharacterCount()).isEqualTo(2);
        assertThat(result.enabledBuildCount()).isEqualTo(2);
        assertThat(autoActivationService.resolve("102550550", buildService.list()))
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina", true),
                        org.assertj.core.groups.Tuple.tuple("noelle", true));
        assertThat(completed.status()).isEqualTo(ArtifactAnalysisJobStatus.COMPLETED);
    }

    private static ArtifactBuild build(String id, String characterKey) {
        return new ArtifactBuild(
                id, id, characterKey, List.of(), Map.of("flower", Set.of("hp")),
                Map.of("critRate_", 1.0), false, false, "test");
    }
}
