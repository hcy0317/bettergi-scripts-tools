package com.cloud_guest.artifact.nativeplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

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
        StringBuilder canonical = new StringBuilder();
        appendInt(canonical, capacity);
        appendInt(canonical, sourceBuildCount);
        appendString(canonical, translationMode);

        appendInt(canonical, lockPlans.size());
        for (ArtifactNativeSetPlan plan : lockPlans) {
            appendString(canonical, plan.buildId());
            appendString(canonical, plan.buildName());
            appendString(canonical, plan.setKey());
            appendString(canonical, plan.slotKey());
            appendSortedStrings(canonical, plan.mainStats());
            appendSortedStrings(canonical, plan.substats());
        }

        appendInt(canonical, quickEquipPlans.size());
        for (ArtifactNativeQuickEquipPlan plan : quickEquipPlans) {
            appendString(canonical, plan.buildId());
            appendString(canonical, plan.buildName());
            appendString(canonical, plan.characterKey());
            appendInt(canonical, plan.presetIndex());
            appendInt(canonical, plan.sets().size());
            plan.sets().forEach(rule -> {
                appendString(canonical, rule.setKey());
                appendInt(canonical, rule.pieces());
            });
            var mainStats = plan.mainStatsBySlot().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .toList();
            appendInt(canonical, mainStats.size());
            mainStats.forEach(entry -> {
                appendString(canonical, entry.getKey());
                appendSortedStrings(canonical, entry.getValue());
            });
            appendStrings(canonical, plan.prioritySubstats());
            appendStrings(canonical, plan.secondarySubstats());
        }

        appendInt(canonical, issues.size());
        for (ArtifactNativePlanIssue issue : issues) {
            appendString(canonical, issue.code());
            appendString(canonical, issue.subjectKey());
            appendStrings(canonical, issue.buildIds());
            appendString(canonical, issue.message());
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendInt(StringBuilder target, int value) {
        target.append(value).append(';');
    }

    private static void appendString(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static void appendStrings(
            StringBuilder target,
            Collection<String> values) {
        appendInt(target, values.size());
        values.forEach(value -> appendString(target, value));
    }

    private static void appendSortedStrings(
            StringBuilder target,
            Collection<String> values) {
        appendStrings(target, values.stream()
                .sorted(Comparator.naturalOrder())
                .toList());
    }
}
