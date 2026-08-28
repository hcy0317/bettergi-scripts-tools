package com.cloud_guest.artifact.nativeplan;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import com.cloud_guest.artifact.domain.ArtifactSetEffectCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArtifactNativePlanCompiler {
    public static final String TRANSLATION_MODE = "CONSERVATIVE_SET_UNION";

    public ArtifactNativeSyncPlan compileReplaceAll(List<ArtifactBuild> builds, int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be nonnegative");
        List<ArtifactBuild> enabled = builds.stream().filter(ArtifactBuild::nativeSyncEnabled).toList();
        if (enabled.isEmpty()) {
            return new ArtifactNativeSyncPlan(
                    ArtifactNativeSyncStatus.NO_GO_EMPTY, false, false, capacity, 0, List.of(),
                    "", TRANSLATION_MODE,
                    "no native-sync-enabled builds were selected");
        }
        Map<PlanKey, MutablePlan> merged = new LinkedHashMap<>();
        for (ArtifactBuild build : enabled) {
            Set<String> positiveSubstats = new LinkedHashSet<>();
            build.substatWeights().forEach((key, weight) -> {
                if (weight > 0) positiveSubstats.add(key);
            });
            for (ArtifactSetRule set : build.allSetRecipes().stream().flatMap(List::stream).toList()) {
                for (String setKey : ArtifactSetEffectCatalog.equivalentSetKeys(set)) {
                    build.mainStatsBySlot().forEach((slot, mainStats) -> {
                        PlanKey key = new PlanKey(setKey, slot);
                        MutablePlan plan = merged.computeIfAbsent(key, ignored -> new MutablePlan());
                        plan.mainStats.addAll(mainStats);
                        plan.substats.addAll(positiveSubstats);
                    });
                }
            }
        }
        long nativeSetPlanCount = merged.keySet().stream().map(PlanKey::setKey).distinct().count();
        if (nativeSetPlanCount > capacity) {
            return new ArtifactNativeSyncPlan(
                    ArtifactNativeSyncStatus.NO_GO_CAPACITY, false, false, capacity,
                    enabled.size(), List.of(), "", TRANSLATION_MODE,
                    "compiled plan count exceeds native plan capacity");
        }
        List<ArtifactNativeSetPlan> plans = merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(PlanKey::setKey).thenComparing(PlanKey::slotKey)))
                .map(entry -> new ArtifactNativeSetPlan(
                        entry.getKey().setKey(), entry.getKey().slotKey(),
                        entry.getValue().mainStats, entry.getValue().substats))
                .toList();
        String digest = ArtifactNativePlanHashes.digest(
                capacity, enabled.size(), TRANSLATION_MODE, plans);
        return new ArtifactNativeSyncPlan(
                ArtifactNativeSyncStatus.READY, true, true, capacity, enabled.size(), plans,
                digest, TRANSLATION_MODE,
                "conservative set-union translation is ready; exact digest and pre-mutation evidence are required");
    }

    private record PlanKey(String setKey, String slotKey) {
    }

    private static final class MutablePlan {
        private final Set<String> mainStats = new LinkedHashSet<>();
        private final Set<String> substats = new LinkedHashSet<>();
    }
}
