package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void singleFieldStateUpdatePreservesTheOtherFieldAndTheBuildDefinition() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        ArtifactBuild original = build(
                "preset-a", "genshin-artifact-analyzer@abc", true, false);
        service.save(original.id(), original);

        ArtifactBuild updated = service.updateState(
                original.id(),
                new ArtifactBuildStateUpdateRequest("analysisEnabled", false));

        assertThat(updated.analysisEnabled()).isFalse();
        assertThat(updated.nativeSyncEnabled()).isFalse();
        assertThat(updated.quickEquipPresetIndex()).isZero();
        assertThat(updated.name()).isEqualTo(original.name());
        assertThat(updated.substatWeights()).isEqualTo(original.substatWeights());
    }

    @Test
    void historicalJsonDefaultsQuickEquipSyncToFalse() throws Exception {
        ArtifactBuild build = new ObjectMapper().readValue("""
                {
                  "id":"preset-a",
                  "name":"preset-a",
                  "characterKey":"Furina",
                  "sets":[],
                  "alternativeSetRecipes":[],
                  "mainStatsBySlot":{"flower":["hp"]},
                  "substatWeights":{"critRate_":1.0},
                  "analysisEnabled":true,
                  "nativeSyncEnabled":true,
                  "sourceVersion":"legacy"
                }
                """, ArtifactBuild.class);

        assertThat(build.quickEquipPresetIndex()).isZero();
    }

    @Test
    void quickEquipPresetSlotsAreExplicitStableAndUnique() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        service.importAll(List.of(
                build("furina-a", "custom", true, true, 1),
                build("furina-b", "custom", true, true, 2),
                build("furina-c", "custom", true, true, 0)));

        assertThatThrownBy(() -> service.updateState(
                "furina-c",
                new ArtifactBuildStateUpdateRequest("quickEquipPresetIndex", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slot");

        service.updateState(
                "furina-a",
                new ArtifactBuildStateUpdateRequest("quickEquipPresetIndex", 0));

        assertThat(service.list())
                .filteredOn(ArtifactBuild::quickEquipSyncEnabled)
                .extracting(ArtifactBuild::id, ArtifactBuild::quickEquipPresetIndex)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("furina-b", 2));
    }

    @Test
    void presetSynchronizationMigratesDefaultLockSelectionToThreePerSet() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());

        List<ArtifactBuild> migrated = service.synchronizePresets(List.of(
                build("a", "genshin-artifact-analyzer@abc", true, true),
                build("b", "genshin-artifact-analyzer@abc", true, true),
                build("c", "genshin-artifact-analyzer@abc", true, true),
                build("d", "genshin-artifact-analyzer@abc", true, true)));

        assertThat(migrated).filteredOn(ArtifactBuild::nativeSyncEnabled)
                .extracting(ArtifactBuild::id)
                .containsExactly("a", "b", "c");
    }

    @Test
    void bundledPresetCannotBeDeletedButCustomBuildCan() {
        ArtifactBuildService service = new ArtifactBuildService(new InMemoryArtifactBuildRepository());
        service.importAll(List.of(
                build("preset-a", "genshin-artifact-analyzer@abc", true, true),
                build("custom-a", "custom", true, true)));

        assertThatThrownBy(() -> service.delete("preset-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("presets");
        assertThat(service.delete("custom-a")).isTrue();
        assertThat(service.list()).extracting(ArtifactBuild::id).containsExactly("preset-a");
    }

    private static ArtifactBuild build(
            String id,
            String sourceVersion,
            boolean analysisEnabled,
            boolean nativeSyncEnabled) {
        return build(id, sourceVersion, analysisEnabled, nativeSyncEnabled, 0);
    }

    private static ArtifactBuild build(
            String id,
            String sourceVersion,
            boolean analysisEnabled,
            boolean nativeSyncEnabled,
            int quickEquipPresetIndex) {
        return new ArtifactBuild(
                id, id, "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("flower", Set.of("hp")),
                Map.of("critRate_", 1.0),
                analysisEnabled, nativeSyncEnabled, quickEquipPresetIndex, sourceVersion);
    }
}
