package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactBuildBulkStateTest {

    @Test
    void updatesEveryUpstreamBuildAndPreservesTheOtherStateField() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        service.importAll(List.of(
                build("preset-a", "genshin-artifact-analyzer@abc", false, true),
                build("preset-b", "genshin-artifact-analyzer@abc", false, true),
                build("custom-a", "custom", false, false)));

        var result = service.updateBulkState(
                new ArtifactBuildBulkStateRequest("upstream", "analysisEnabled", true));

        assertThat(result)
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled, ArtifactBuild::nativeSyncEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("custom-a", false, false),
                        org.assertj.core.groups.Tuple.tuple("preset-a", true, true),
                        org.assertj.core.groups.Tuple.tuple("preset-b", true, true));
    }

    @Test
    void allScopeUpdatesEveryBuildIncludingCustomBuilds() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        service.importAll(List.of(
                build("preset-a", "genshin-artifact-analyzer@abc", true, false),
                build("custom-a", "custom", true, false)));

        var result = service.updateBulkState(
                new ArtifactBuildBulkStateRequest("all", "nativeSyncEnabled", true));

        assertThat(result).allMatch(ArtifactBuild::nativeSyncEnabled);
    }

    private static ArtifactBuild build(
            String id,
            String sourceVersion,
            boolean analysisEnabled,
            boolean nativeSyncEnabled) {
        return new ArtifactBuild(
                id, id, "Furina", List.of(),
                Map.of("flower", Set.of("hp")),
                Map.of("critRate_", 1.0),
                analysisEnabled, nativeSyncEnabled, sourceVersion);
    }
}
