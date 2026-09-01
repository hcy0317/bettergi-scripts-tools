package com.cloud_guest.artifact.nativeplan;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactNativePlanCompilerTest {

    private final ArtifactNativePlanCompiler compiler = new ArtifactNativePlanCompiler();

    @Test
    void preservesBuildIdentityAndUsesTheStrictStrongSubstatThreshold() {
        ArtifactBuild build = build(
                "furina-crit", "Furina", true, true,
                List.of(new ArtifactSetRule("GoldenTroupe", 4)), List.of(),
                weights("critRate_", 1.0, "eleMas", 0.9, "enerRech_", 0.8));

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(List.of(build), 10);

        assertThat(plan.status()).isEqualTo(ArtifactNativeSyncStatus.READY);
        assertThat(plan.translationMode())
                .isEqualTo("BUILD_SCOPED_LOCK_AND_QUICK_EQUIP_V1");
        assertThat(plan.planDigest()).hasSize(64);
        assertThat(plan.lockPlans()).containsExactly(new ArtifactNativeSetPlan(
                "furina-crit", "furina-crit", "GoldenTroupe", "circlet",
                Set.of("critRate_", "critDMG_"), Set.of("critRate_", "eleMas")));
        assertThat(plan.quickEquipPlans()).containsExactly(new ArtifactNativeQuickEquipPlan(
                "furina-crit", "furina-crit", "Furina", 1,
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("critRate_", "critDMG_")),
                List.of("critRate_", "eleMas"), List.of()));
    }

    @Test
    void threeLockPlansPerSetAreReadyButTheFourthIsNoGo() {
        List<ArtifactBuild> three = List.of(
                build("a", "Furina", true, false),
                build("b", "Neuvillette", true, false),
                build("c", "Yelan", true, false));

        ArtifactNativeSyncPlan ready = compiler.compileReplaceAll(three, 10);
        ArtifactNativeSyncPlan rejected = compiler.compileReplaceAll(
                List.of(three.get(0), three.get(1), three.get(2),
                        build("d", "Xingqiu", true, false)), 10);

        assertThat(ready.status()).isEqualTo(ArtifactNativeSyncStatus.READY);
        assertThat(ready.lockPlans()).extracting(ArtifactNativeSetPlan::buildId)
                .containsExactly("a", "b", "c");
        assertThat(rejected.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_CAPACITY);
        assertThat(rejected.replaceLockPlans()).isFalse();
        assertThat(rejected.lockPlans()).isEmpty();
        assertThat(rejected.issues()).extracting(ArtifactNativePlanIssue::code)
                .contains("LOCK_SET_BUILD_LIMIT");
    }

    @Test
    void twoQuickEquipBuildsPerCharacterAreStableButTheThirdIsNoGo() {
        ArtifactNativeSyncPlan ready = compiler.compileReplaceAll(List.of(
                build("furina-a", "Furina", false, true),
                build("furina-b", "Furina", false, true)), 10);
        ArtifactNativeSyncPlan rejected = compiler.compileReplaceAll(List.of(
                build("furina-a", "Furina", false, true),
                build("furina-b", "Furina", false, true),
                build("furina-c", "Furina", false, true)), 10);

        assertThat(ready.quickEquipPlans())
                .extracting(ArtifactNativeQuickEquipPlan::buildId,
                        ArtifactNativeQuickEquipPlan::presetIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina-a", 1),
                        org.assertj.core.groups.Tuple.tuple("furina-b", 2));
        assertThat(rejected.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_CAPACITY);
        assertThat(rejected.quickEquipPlans()).isEmpty();
        assertThat(rejected.issues()).extracting(ArtifactNativePlanIssue::code)
                .contains("QUICK_EQUIP_CHARACTER_LIMIT");
    }

    @Test
    void alternativeRecipesExpandOnlyLockPlans() {
        ArtifactBuild build = build(
                "furina", "Furina", true, true,
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                List.of(List.of(new ArtifactSetRule("MarechausseeHunter", 4))),
                weights("critRate_", 1.0));

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(List.of(build), 10);

        assertThat(plan.lockPlans()).extracting(ArtifactNativeSetPlan::setKey)
                .containsExactly("GoldenTroupe", "MarechausseeHunter");
        assertThat(plan.quickEquipPlans()).singleElement().satisfies(quick ->
                assertThat(quick.sets()).containsExactly(
                        new ArtifactSetRule("GoldenTroupe", 4)));
    }

    @Test
    void quickEquipSplitsFourStrongSubstatsIntoThreePriorityAndOneSecondary() {
        ArtifactBuild build = build(
                "mona-nuke", "Mona", false, true,
                List.of(new ArtifactSetRule("HeartOfDepth", 4)), List.of(),
                weights("critRate_", 1.0, "critDMG_", 1.0,
                        "atk_", 0.9, "eleMas", 0.9, "enerRech_", 0.8));

        ArtifactNativeQuickEquipPlan plan = compiler.compileReplaceAll(List.of(build), 10)
                .quickEquipPlans().getFirst();

        assertThat(plan.prioritySubstats())
                .containsExactly("critDMG_", "critRate_", "atk_");
        assertThat(plan.secondarySubstats()).containsExactly("eleMas");
    }

    @Test
    void capacityFailureAndUnrepresentableBuildsAreNoGoBeforeMutation() {
        ArtifactNativeSyncPlan capacity = compiler.compileReplaceAll(List.of(
                build("first", "Furina", true, false),
                build("second", "Yelan", true, false,
                        List.of(new ArtifactSetRule("MarechausseeHunter", 4)),
                        List.of(), weights("critDMG_", 1.0))), 1);
        ArtifactBuild invalid = new ArtifactBuild(
                "invalid", "invalid", "Furina", List.of(), Map.of(),
                Map.of("critRate_", 1.0), true, false, false, "custom");
        ArtifactNativeSyncPlan empty = compiler.compileReplaceAll(List.of(invalid), 10);

        assertThat(capacity.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_CAPACITY);
        assertThat(capacity.lockPlans()).isEmpty();
        assertThat(empty.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_EMPTY);
        assertThat(empty.replaceLockPlans()).isFalse();
    }

    @Test
    void digestChangesWhenQuickEquipSelectionChanges() {
        ArtifactBuild selected = build("furina", "Furina", true, true);
        ArtifactBuild lockOnly = selected.withStates(true, true, false);

        assertThat(compiler.compileReplaceAll(List.of(selected), 10).planDigest())
                .isNotEqualTo(compiler.compileReplaceAll(List.of(lockOnly), 10).planDigest());
    }

    private static ArtifactBuild build(
            String id,
            String characterKey,
            boolean nativeSyncEnabled,
            boolean quickEquipSyncEnabled) {
        return build(id, characterKey, nativeSyncEnabled, quickEquipSyncEnabled,
                List.of(new ArtifactSetRule("GoldenTroupe", 4)), List.of(),
                weights("critRate_", 1.0));
    }

    private static ArtifactBuild build(
            String id,
            String characterKey,
            boolean nativeSyncEnabled,
            boolean quickEquipSyncEnabled,
            List<ArtifactSetRule> sets,
            List<List<ArtifactSetRule>> alternatives,
            Map<String, Double> weights) {
        return new ArtifactBuild(
                id, id, characterKey, sets, alternatives,
                Map.of("circlet", Set.of("critRate_", "critDMG_")),
                weights, true, nativeSyncEnabled, quickEquipSyncEnabled, "source");
    }

    private static Map<String, Double> weights(Object... values) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], (Double) values[index + 1]);
        }
        return result;
    }
}
