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
                "furina-crit", "Furina", true, 1,
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
                build("a", "Furina", true, 0),
                build("b", "Neuvillette", true, 0),
                build("c", "Yelan", true, 0));

        ArtifactNativeSyncPlan ready = compiler.compileReplaceAll(three, 10);
        ArtifactNativeSyncPlan rejected = compiler.compileReplaceAll(
                List.of(three.get(0), three.get(1), three.get(2),
                        build("d", "Xingqiu", true, 0)), 10);

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
                build("furina-a", "Furina", false, 1),
                build("furina-b", "Furina", false, 2)), 10);
        ArtifactNativeSyncPlan rejected = compiler.compileReplaceAll(List.of(
                build("furina-a", "Furina", false, 1),
                build("furina-b", "Furina", false, 2),
                build("furina-c", "Furina", false, 1)), 10);

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
                "furina", "Furina", true, 1,
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
                "mona-nuke", "Mona", false, 1,
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
                build("first", "Furina", true, 0),
                build("second", "Yelan", true, 0,
                        List.of(new ArtifactSetRule("MarechausseeHunter", 4)),
                        List.of(), weights("critDMG_", 1.0))), 1);
        ArtifactBuild invalid = new ArtifactBuild(
                "invalid", "invalid", "Furina", List.of(), Map.of(),
                Map.of("critRate_", 1.0), true, false, 0, "custom");
        ArtifactNativeSyncPlan empty = compiler.compileReplaceAll(List.of(invalid), 10);

        assertThat(capacity.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_CAPACITY);
        assertThat(capacity.lockPlans()).isEmpty();
        assertThat(empty.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_EMPTY);
        assertThat(empty.replaceLockPlans()).isFalse();
    }

    @Test
    void lockSelectionWithNoSlotPlansIsRejectedEvenWhenQuickEquipIsValid() {
        ArtifactBuild invalidLock = new ArtifactBuild(
                "invalid-lock", "invalid-lock", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of(), Map.of("critRate_", 1.0),
                true, true, 1, "custom");

        ArtifactNativeSyncPlan plan = compiler.compileReplaceAll(
                List.of(invalidLock), 10);

        assertThat(plan.status()).isEqualTo(ArtifactNativeSyncStatus.NO_GO_EMPTY);
        assertThat(plan.issues()).extracting(ArtifactNativePlanIssue::code)
                .contains("LOCK_BUILD_UNREPRESENTABLE");
    }

    @Test
    void digestLengthPrefixesBuildIdentityFields() {
        ArtifactBuild delimitedId = new ArtifactBuild(
                "a|b", "c", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("critRate_")),
                Map.of("critDMG_", 1.0), true, false, 1, "custom");
        ArtifactBuild delimitedName = new ArtifactBuild(
                "a", "b|c", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("critRate_")),
                Map.of("critDMG_", 1.0), true, false, 1, "custom");

        assertThat(compiler.compileReplaceAll(List.of(delimitedId), 10).planDigest())
                .isNotEqualTo(compiler.compileReplaceAll(
                        List.of(delimitedName), 10).planDigest());
    }

    @Test
    void digestChangesWhenQuickEquipSelectionChanges() {
        ArtifactBuild selected = build("furina", "Furina", true, 1);
        ArtifactBuild lockOnly = selected.withQuickEquipPresetIndex(0);

        assertThat(compiler.compileReplaceAll(List.of(selected), 10).planDigest())
                .isNotEqualTo(compiler.compileReplaceAll(List.of(lockOnly), 10).planDigest());
    }

    private static ArtifactBuild build(
            String id,
            String characterKey,
            boolean nativeSyncEnabled,
            int quickEquipPresetIndex) {
        return build(id, characterKey, nativeSyncEnabled, quickEquipPresetIndex,
                List.of(new ArtifactSetRule("GoldenTroupe", 4)), List.of(),
                weights("critRate_", 1.0));
    }

    private static ArtifactBuild build(
            String id,
            String characterKey,
            boolean nativeSyncEnabled,
            int quickEquipPresetIndex,
            List<ArtifactSetRule> sets,
            List<List<ArtifactSetRule>> alternatives,
            Map<String, Double> weights) {
        return new ArtifactBuild(
                id, id, characterKey, sets, alternatives,
                Map.of("circlet", Set.of("critRate_", "critDMG_")),
                weights, true, nativeSyncEnabled, quickEquipPresetIndex, "source");
    }

    private static Map<String, Double> weights(Object... values) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], (Double) values[index + 1]);
        }
        return result;
    }
}
