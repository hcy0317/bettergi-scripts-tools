package com.cloud_guest.controller;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisEngine;
import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import com.cloud_guest.artifact.build.ArtifactBuildService;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettingsService;
import com.cloud_guest.artifact.build.InMemoryArtifactBuildAutoActivationSettingsRepository;
import com.cloud_guest.artifact.build.InMemoryArtifactBuildAutoActivationResultRepository;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationService;
import com.cloud_guest.artifact.build.InMemoryArtifactBuildRepository;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import com.cloud_guest.artifact.execution.ArtifactExecutionGuard;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobService;
import com.cloud_guest.artifact.job.InMemoryArtifactAnalysisJobRepository;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.nativeplan.ArtifactNativePlanCompiler;
import com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactAnalysisControllerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void managesBuildsSettingsAndStartsAnalysisFromTheWebBoundary() {
        ArtifactAnalysisController controller = controller();
        ArtifactBuild build = build();

        assertThat(controller.saveBuild(build.id(), build, "102550550").getData()).isEqualTo(build);
        assertThat(controller.builds("102550550").getData()).containsExactly(build);

        ArtifactAnalysisPolicy policy = new ArtifactAnalysisPolicy(78, 83, 0.25);
        assertThat(controller.saveSettings(policy, "102550550").getData()).isEqualTo(policy);
        assertThat(controller.settings().getData()).isEqualTo(policy);

        var start = controller.startJob(
                "102550550", ArtifactLaunchOperation.ANALYZE, 100, false, "").getData();
        assertThat(start.job().uid()).isEqualTo("102550550");
        assertThat(start.launch().launchUri()).startsWith("BetterGIArtifact://analysis?request=");
        assertThat(controller.jobs("102550550").getData())
                .extracting(com.cloud_guest.artifact.job.ArtifactAnalysisJobSummary::id)
                .containsExactly(start.job().id());
    }

    @Test
    void previewsCompleteNativeReplacementWithoutMutatingTheGame() {
        ArtifactAnalysisController controller = controller();
        controller.saveBuild(build().id(), build(), "102550550");

        var preview = controller.previewNativeSync(100, "102550550").getData();

        assertThat(preview.status()).isEqualTo(ArtifactNativeSyncStatus.READY);
        assertThat(preview.replaceAll()).isTrue();
        assertThat(preview.requiresPreMutationEvidence()).isTrue();
        assertThat(preview.plans()).isNotEmpty();

        assertThatThrownBy(() -> controller.startJob(
                "102550550", ArtifactLaunchOperation.REBUILD_NATIVE_PLANS, 100, false, ""))
                .hasMessageContaining("confirmation");
        assertThatThrownBy(() -> controller.startJob(
                "102550550", ArtifactLaunchOperation.REBUILD_NATIVE_PLANS,
                100, true, "stale"))
                .hasMessageContaining("changed");
        var start = controller.startJob(
                "102550550", ArtifactLaunchOperation.REBUILD_NATIVE_PLANS,
                100, true, preview.planDigest())
                .getData();
        assertThat(start.launch().launchUri()).startsWith("BetterGIArtifact://native-sync?request=");
    }

    @Test
    void persistsAutoActivationSettingsAndStartsCharacterDetection() {
        ArtifactAnalysisController controller = controller();
        ArtifactBuildAutoActivationSettings settings =
                new ArtifactBuildAutoActivationSettings(80, true);

        assertThat(controller.saveAutoActivationSettings(settings).getData()).isEqualTo(settings);
        assertThat(controller.autoActivationSettings().getData()).isEqualTo(settings);
        var start = controller.startCharacterRosterJob(
                "102550550", "眇", "遥", "MannequinGirl").getData();

        assertThat(start.job().operation())
                .isEqualTo(ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER);
        assertThat(start.launch().launchUri())
                .startsWith("BetterGIArtifact://characters?request=");
    }

    @Test
    void genericJobEndpointRejectsCharacterAndLockOperations() {
        ArtifactAnalysisController controller = controller();

        assertThatThrownBy(() -> controller.startJob(
                "102550550", ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER,
                100, false, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only accepts ANALYZE");
        assertThatThrownBy(() -> controller.startJob(
                "102550550", ArtifactLaunchOperation.EXECUTE_LOCK_PLAN,
                100, false, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ArtifactAnalysisController controller() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        ArtifactBuildService buildService = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        ArtifactAnalysisSettingsService settingsService = new ArtifactAnalysisSettingsService(
                new InMemoryArtifactAnalysisSettingsRepository());
        ArtifactBuildAutoActivationSettingsService autoActivationSettingsService =
                new ArtifactBuildAutoActivationSettingsService(
                        new InMemoryArtifactBuildAutoActivationSettingsRepository());
        ArtifactAnalysisJobService jobService = new ArtifactAnalysisJobService(
                new InMemoryArtifactAnalysisJobRepository(),
                new ArtifactAnalysisEngine(),
                new ArtifactExecutionGuard(),
                new ArtifactLaunchRequestService(
                        tempDirectory, new ObjectMapper(), clock, Duration.ofMinutes(5)),
                clock);
        return new ArtifactAnalysisController(
                buildService, settingsService, autoActivationSettingsService,
                new ArtifactBuildAutoActivationService(
                        buildService,
                        new InMemoryArtifactBuildAutoActivationResultRepository()),
                jobService, new ArtifactNativePlanCompiler());
    }

    private static ArtifactBuild build() {
        return new ArtifactBuild(
                "furina", "后台C", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("sands", Set.of("hp_", "enerRech_"),
                        "goblet", Set.of("hp_", "hydro_dmg_"),
                        "circlet", Set.of("critRate_", "critDMG_", "hp_")),
                Map.of("critRate_", 1.0, "critDMG_", 1.0, "hp_", 1.0, "enerRech_", 0.5),
                true, true, "genshin-artifact-analyzer@766b1a6a");
    }
}
