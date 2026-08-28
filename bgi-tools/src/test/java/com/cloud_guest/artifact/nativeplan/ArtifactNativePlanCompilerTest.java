package com.cloud_guest.artifact.nativeplan;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactNativePlanCompilerTest {

    private final ArtifactNativePlanCompiler compiler = new ArtifactNativePlanCompiler();

    @Test
    void completeReplacementMergesAllNativeEnabledBuildsConservatively() {
        ArtifactBuild crit = build(
                "furina-crit", Set.of("critRate_", "critDMG_"), Set.of("critRate_", "critDMG_"));
        ArtifactBuild hp = build(
                "furina-hp", Set.of("hp_"), Set.of("hp_", "enerRech_"));

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(List.of(crit, hp), 10);

        assertThat(plan.status()).isEqualTo(ArtifactNativeSyncStatus.READY);
        assertThat(plan.replaceAll()).isTrue();
        assertThat(plan.requiresPreMutationEvidence()).isTrue();
        assertThat(plan.capacity()).isEqualTo(10);
        assertThat(plan.translationMode()).isEqualTo("CONSERVATIVE_SET_UNION");
        assertThat(plan.planDigest()).hasSize(64);
        assertThat(plan.plans()).containsExactly(new ArtifactNativeSetPlan(
                "GoldenTroupe", "circlet",
                Set.of("critRate_", "critDMG_", "hp_"),
                Set.of("critRate_", "critDMG_", "hp_", "enerRech_")));
    }

    @Test
    void capacityFailureIsNoGoBeforeDeletingExistingPlans() {
        ArtifactBuild first = build("first", Set.of("critRate_"), Set.of("critRate_"));
        ArtifactBuild second = new ArtifactBuild(
                "second", "second", "Furina",
                List.of(new ArtifactSetRule("MarechausseeHunter", 4)),
                Map.of("circlet", Set.of("critDMG_")),
                Map.of("critDMG_", 1.0), true, true, "source");

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(List.of(first, second), 1);

        assertThat(plan.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_CAPACITY);
        assertThat(plan.replaceAll()).isFalse();
        assertThat(plan.plans()).isEmpty();
        assertThat(plan.message()).contains("capacity");
    }

    @Test
    void twoPieceRulesExpandSetsWithTheSameEffectForNativeSync() {
        ArtifactBuild build = new ArtifactBuild(
                "healer", "healer", "Furina",
                List.of(
                        new ArtifactSetRule("MaidenBeloved", 4),
                        new ArtifactSetRule("TenacityOfTheMillelith", 4)),
                Map.of("circlet", Set.of("heal_")),
                Map.of("hp_", 1.0), true, true, "source");

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(List.of(build), 10);

        assertThat(plan.status()).isEqualTo(ArtifactNativeSyncStatus.READY);
        assertThat(plan.plans()).extracting(ArtifactNativeSetPlan::setKey)
                .contains("TenacityOfTheMillelith", "VourukashasGlow");
    }

    private static ArtifactBuild build(String id, Set<String> mains, Set<String> substats) {
        return new ArtifactBuild(
                id, id, "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", mains),
                substats.stream().collect(java.util.stream.Collectors.toMap(key -> key, key -> 1.0)),
                true, true, "source");
    }
}
