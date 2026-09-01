package com.cloud_guest.artifact.nativeplan;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetEffectCatalog;
import com.cloud_guest.artifact.domain.ArtifactSetRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ArtifactNativePlanCompiler {
    public static final String TRANSLATION_MODE =
            "BUILD_SCOPED_LOCK_AND_QUICK_EQUIP_V1";
    private static final double STRONG_SUBSTAT_THRESHOLD = 0.8;
    private static final int MAX_LOCK_PLANS_PER_SET = 3;
    private static final int MAX_QUICK_EQUIP_PLANS_PER_CHARACTER = 2;
    private static final int MAX_QUICK_EQUIP_SUBSTATS = 6;

    public ArtifactNativeSyncPlan compileReplaceAll(
            List<ArtifactBuild> builds,
            int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be nonnegative");
        }
        List<ArtifactBuild> lockBuilds = builds.stream()
                .filter(ArtifactBuild::nativeSyncEnabled)
                .sorted(Comparator.comparing(ArtifactBuild::id))
                .toList();
        List<ArtifactBuild> quickBuilds = builds.stream()
                .filter(ArtifactBuild::quickEquipSyncEnabled)
                .sorted(Comparator.comparing(ArtifactBuild::characterKey)
                        .thenComparing(ArtifactBuild::id))
                .toList();
        int sourceBuildCount = selectedBuildCount(lockBuilds, quickBuilds);
        if (sourceBuildCount == 0) {
            return noGo(
                    ArtifactNativeSyncStatus.NO_GO_EMPTY,
                    capacity,
                    0,
                    List.of(),
                    "no native plan builds were selected");
        }

        List<ArtifactNativePlanIssue> issues = new ArrayList<>();
        List<ArtifactNativeSetPlan> lockPlans = compileLockPlans(lockBuilds, issues);
        List<ArtifactNativeQuickEquipPlan> quickPlans =
                compileQuickEquipPlans(quickBuilds, issues);

        Set<String> lockSetKeys = lockPlans.stream()
                .map(ArtifactNativeSetPlan::setKey)
                .collect(Collectors.toCollection(TreeSet::new));
        if (lockSetKeys.size() > capacity) {
            issues.add(new ArtifactNativePlanIssue(
                    "LOCK_SET_CAPACITY",
                    "lock-assistance",
                    lockBuilds.stream().map(ArtifactBuild::id).toList(),
                    "compiled set count exceeds native plan capacity"));
        }
        lockPlans.stream()
                .collect(Collectors.groupingBy(
                        ArtifactNativeSetPlan::setKey,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                ArtifactNativeSetPlan::buildId,
                                Collectors.toCollection(TreeSet::new))))
                .forEach((setKey, buildIds) -> {
                    if (buildIds.size() > MAX_LOCK_PLANS_PER_SET) {
                        issues.add(new ArtifactNativePlanIssue(
                                "LOCK_SET_BUILD_LIMIT",
                                setKey,
                                List.copyOf(buildIds),
                                "artifact set supports at most three build plans"));
                    }
                });

        if (!issues.isEmpty()) {
            ArtifactNativeSyncStatus status = issues.stream()
                    .allMatch(issue -> issue.code().contains("CAPACITY")
                            || issue.code().contains("LIMIT"))
                    ? ArtifactNativeSyncStatus.NO_GO_CAPACITY
                    : ArtifactNativeSyncStatus.NO_GO_EMPTY;
            return noGo(
                    status,
                    capacity,
                    sourceBuildCount,
                    issues,
                    issues.getFirst().message());
        }
        if (lockPlans.isEmpty() && quickPlans.isEmpty()) {
            return noGo(
                    ArtifactNativeSyncStatus.NO_GO_EMPTY,
                    capacity,
                    sourceBuildCount,
                    List.of(),
                    "selected builds did not compile into representable plans");
        }

        String digest = ArtifactNativePlanHashes.digest(
                capacity,
                sourceBuildCount,
                TRANSLATION_MODE,
                lockPlans,
                quickPlans,
                List.of());
        return new ArtifactNativeSyncPlan(
                ArtifactNativeSyncStatus.READY,
                !lockPlans.isEmpty(),
                true,
                capacity,
                sourceBuildCount,
                lockPlans,
                quickPlans,
                List.of(),
                digest,
                TRANSLATION_MODE,
                "build-scoped lock and quick-equip plans are ready");
    }

    private static List<ArtifactNativeSetPlan> compileLockPlans(
            List<ArtifactBuild> builds,
            List<ArtifactNativePlanIssue> issues) {
        Map<LockPlanKey, MutableLockPlan> plans = new LinkedHashMap<>();
        for (ArtifactBuild build : builds) {
            List<String> strongSubstats = strongSubstats(build);
            Set<String> setKeys = build.allSetRecipes().stream()
                    .flatMap(List::stream)
                    .flatMap(rule -> ArtifactSetEffectCatalog
                            .equivalentSetKeys(rule).stream())
                    .collect(Collectors.toCollection(TreeSet::new));
            if (setKeys.isEmpty()) {
                issues.add(new ArtifactNativePlanIssue(
                        "LOCK_BUILD_UNREPRESENTABLE",
                        build.id(),
                        List.of(build.id()),
                        "lock-enabled build has no representable artifact set"));
                continue;
            }
            for (String setKey : setKeys) {
                build.mainStatsBySlot().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            LockPlanKey key = new LockPlanKey(
                                    build.id(), build.name(), setKey, entry.getKey());
                            MutableLockPlan plan = plans.computeIfAbsent(
                                    key, ignored -> new MutableLockPlan());
                            plan.mainStats.addAll(entry.getValue());
                            plan.substats.addAll(strongSubstats);
                        });
            }
        }
        return plans.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(LockPlanKey::setKey)
                                .thenComparing(LockPlanKey::buildId)
                                .thenComparing(LockPlanKey::slotKey)))
                .map(entry -> new ArtifactNativeSetPlan(
                        entry.getKey().buildId(),
                        entry.getKey().buildName(),
                        entry.getKey().setKey(),
                        entry.getKey().slotKey(),
                        entry.getValue().mainStats,
                        entry.getValue().substats))
                .toList();
    }

    private static List<ArtifactNativeQuickEquipPlan> compileQuickEquipPlans(
            List<ArtifactBuild> builds,
            List<ArtifactNativePlanIssue> issues) {
        List<ArtifactNativeQuickEquipPlan> plans = new ArrayList<>();
        Map<String, List<ArtifactBuild>> byCharacter = builds.stream()
                .collect(Collectors.groupingBy(
                        ArtifactBuild::characterKey,
                        LinkedHashMap::new,
                        Collectors.toList()));
        byCharacter.forEach((characterKey, characterBuilds) -> {
            if (characterKey.isBlank()) {
                issues.add(new ArtifactNativePlanIssue(
                        "QUICK_EQUIP_CHARACTER_REQUIRED",
                        "",
                        characterBuilds.stream().map(ArtifactBuild::id).toList(),
                        "quick-equip build requires a character key"));
                return;
            }
            if (characterBuilds.size() > MAX_QUICK_EQUIP_PLANS_PER_CHARACTER) {
                issues.add(new ArtifactNativePlanIssue(
                        "QUICK_EQUIP_CHARACTER_LIMIT",
                        characterKey,
                        characterBuilds.stream().map(ArtifactBuild::id).toList(),
                        "character supports at most two quick-equip builds"));
                return;
            }
            for (int index = 0; index < characterBuilds.size(); index++) {
                ArtifactBuild build = characterBuilds.get(index);
                if (build.sets().isEmpty()) {
                    issues.add(new ArtifactNativePlanIssue(
                            "QUICK_EQUIP_BUILD_UNREPRESENTABLE",
                            characterKey,
                            List.of(build.id()),
                            "quick-equip build has no primary artifact recipe"));
                    continue;
                }
                List<String> strongSubstats = strongSubstats(build);
                if (strongSubstats.size() > MAX_QUICK_EQUIP_SUBSTATS) {
                    issues.add(new ArtifactNativePlanIssue(
                            "QUICK_EQUIP_SUBSTAT_LIMIT",
                            build.id(),
                            List.of(build.id()),
                            "quick-equip build has more than six strong substats"));
                    continue;
                }
                plans.add(new ArtifactNativeQuickEquipPlan(
                        build.id(),
                        build.name(),
                        characterKey,
                        index + 1,
                        build.sets(),
                        sortedMainStats(build.mainStatsBySlot()),
                        strongSubstats.stream().limit(3).toList(),
                        strongSubstats.stream().skip(3).limit(3).toList()));
            }
        });
        return List.copyOf(plans);
    }

    private static List<String> strongSubstats(ArtifactBuild build) {
        return build.substatWeights().entrySet().stream()
                .filter(entry -> entry.getValue() > STRONG_SUBSTAT_THRESHOLD)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, Set<String>> sortedMainStats(
            Map<String, Set<String>> mainStatsBySlot) {
        Map<String, Set<String>> sorted = new LinkedHashMap<>();
        mainStatsBySlot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(
                        entry.getKey(),
                        new LinkedHashSet<>(new TreeSet<>(entry.getValue()))));
        return sorted;
    }

    private static int selectedBuildCount(
            List<ArtifactBuild> lockBuilds,
            List<ArtifactBuild> quickBuilds) {
        Set<String> selected = new LinkedHashSet<>();
        lockBuilds.forEach(build -> selected.add(build.id()));
        quickBuilds.forEach(build -> selected.add(build.id()));
        return selected.size();
    }

    private static ArtifactNativeSyncPlan noGo(
            ArtifactNativeSyncStatus status,
            int capacity,
            int sourceBuildCount,
            List<ArtifactNativePlanIssue> issues,
            String message) {
        return new ArtifactNativeSyncPlan(
                status,
                false,
                false,
                capacity,
                sourceBuildCount,
                List.of(),
                List.of(),
                issues,
                "",
                TRANSLATION_MODE,
                message);
    }

    private record LockPlanKey(
            String buildId,
            String buildName,
            String setKey,
            String slotKey) {
    }

    private static final class MutableLockPlan {
        private final Set<String> mainStats = new TreeSet<>();
        private final Set<String> substats = new LinkedHashSet<>();
    }
}
