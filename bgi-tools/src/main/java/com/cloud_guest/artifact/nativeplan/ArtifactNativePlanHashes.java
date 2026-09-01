package com.cloud_guest.artifact.nativeplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ArtifactNativePlanHashes {
    private ArtifactNativePlanHashes() {
    }

    static String digest(
            int capacity,
            int sourceBuildCount,
            String translationMode,
            List<ArtifactNativeSetPlan> lockPlans,
            List<ArtifactNativeQuickEquipPlan> quickEquipPlans,
            List<ArtifactNativePlanIssue> issues) {
        String canonicalLockPlans = lockPlans.stream()
                .map(plan -> String.join("|",
                        plan.buildId(), plan.buildName(), plan.setKey(), plan.slotKey(),
                        sorted(plan.mainStats()), sorted(plan.substats())))
                .collect(Collectors.joining("\n"));
        String canonicalQuickPlans = quickEquipPlans.stream()
                .map(plan -> String.join("|",
                        plan.buildId(), plan.buildName(), plan.characterKey(),
                        Integer.toString(plan.presetIndex()),
                        plan.sets().stream()
                                .map(rule -> rule.setKey() + ":" + rule.pieces())
                                .collect(Collectors.joining(",")),
                        plan.mainStatsBySlot().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> entry.getKey() + "=" + sorted(entry.getValue()))
                                .collect(Collectors.joining(";")),
                        String.join(",", plan.prioritySubstats()),
                        String.join(",", plan.secondarySubstats())))
                .collect(Collectors.joining("\n"));
        String canonicalIssues = issues.stream()
                .map(issue -> String.join("|",
                        issue.code(), issue.subjectKey(),
                        String.join(",", issue.buildIds()), issue.message()))
                .collect(Collectors.joining("\n"));
        String canonical = capacity + "|" + sourceBuildCount + "|" + translationMode
                + "\nLOCK\n" + canonicalLockPlans
                + "\nQUICK\n" + canonicalQuickPlans
                + "\nISSUES\n" + canonicalIssues;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sorted(java.util.Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }
}
