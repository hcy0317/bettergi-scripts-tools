package com.cloud_guest.artifact.nativeplan;

import com.cloud_guest.artifact.domain.ArtifactSetRule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ArtifactNativeQuickEquipPlan(
        String buildId,
        String buildName,
        String characterKey,
        int presetIndex,
        List<ArtifactSetRule> sets,
        Map<String, Set<String>> mainStatsBySlot,
        List<String> prioritySubstats,
        List<String> secondarySubstats) {

    public ArtifactNativeQuickEquipPlan {
        sets = List.copyOf(sets);
        Map<String, Set<String>> copiedMainStats = new LinkedHashMap<>();
        mainStatsBySlot.forEach((slot, stats) -> copiedMainStats.put(
                slot, Collections.unmodifiableSet(new LinkedHashSet<>(stats))));
        mainStatsBySlot = Collections.unmodifiableMap(copiedMainStats);
        prioritySubstats = List.copyOf(prioritySubstats);
        secondarySubstats = List.copyOf(secondarySubstats);
    }
}
