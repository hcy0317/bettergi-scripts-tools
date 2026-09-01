package com.cloud_guest.artifact.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ArtifactBuild(
        String id,
        String name,
        String characterKey,
        List<ArtifactSetRule> sets,
        List<List<ArtifactSetRule>> alternativeSetRecipes,
        Map<String, Set<String>> mainStatsBySlot,
        Map<String, Double> substatWeights,
        boolean analysisEnabled,
        boolean nativeSyncEnabled,
        boolean quickEquipSyncEnabled,
        String sourceVersion) {

    public ArtifactBuild {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("build id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("build name is required");
        characterKey = characterKey == null ? "" : characterKey;
        sets = normalizeRecipe(sets);
        alternativeSetRecipes = alternativeSetRecipes == null
                ? List.of()
                : alternativeSetRecipes.stream().map(ArtifactBuild::normalizeRecipe).toList();
        Map<String, Set<String>> mainStats = new LinkedHashMap<>();
        if (mainStatsBySlot != null) {
            mainStatsBySlot.forEach((slot, stats) -> mainStats.put(slot, Set.copyOf(new LinkedHashSet<>(stats))));
        }
        mainStatsBySlot = Map.copyOf(mainStats);
        Map<String, Double> weights = new LinkedHashMap<>();
        if (substatWeights != null) {
            substatWeights.forEach((key, weight) -> {
                if (weight == null || !Double.isFinite(weight) || weight < 0 || weight > 1) {
                    throw new IllegalArgumentException("substat weight must be in [0, 1]");
                }
                weights.put(key, weight);
            });
        }
        substatWeights = Map.copyOf(weights);
        sourceVersion = sourceVersion == null ? "" : sourceVersion;
    }

    public ArtifactBuild(
            String id,
            String name,
            String characterKey,
            List<ArtifactSetRule> sets,
            List<List<ArtifactSetRule>> alternativeSetRecipes,
            Map<String, Set<String>> mainStatsBySlot,
            Map<String, Double> substatWeights,
            boolean analysisEnabled,
            boolean nativeSyncEnabled,
            String sourceVersion) {
        this(id, name, characterKey, sets, alternativeSetRecipes,
                mainStatsBySlot, substatWeights, analysisEnabled, nativeSyncEnabled,
                false, sourceVersion);
    }

    public ArtifactBuild(
            String id,
            String name,
            String characterKey,
            List<ArtifactSetRule> sets,
            Map<String, Set<String>> mainStatsBySlot,
            Map<String, Double> substatWeights,
            boolean analysisEnabled,
            boolean nativeSyncEnabled,
            String sourceVersion) {
        this(id, name, characterKey, sets, List.of(), mainStatsBySlot, substatWeights,
                analysisEnabled, nativeSyncEnabled, false, sourceVersion);
    }

    public ArtifactBuild(
            String id,
            String name,
            String characterKey,
            List<ArtifactSetRule> sets,
            Map<String, Set<String>> mainStatsBySlot,
            Map<String, Double> substatWeights,
            boolean analysisEnabled,
            boolean nativeSyncEnabled,
            boolean quickEquipSyncEnabled,
            String sourceVersion) {
        this(id, name, characterKey, sets, List.of(), mainStatsBySlot, substatWeights,
                analysisEnabled, nativeSyncEnabled, quickEquipSyncEnabled, sourceVersion);
    }

    public boolean acceptsMainStat(ArtifactItem artifact) {
        return mainStatsBySlot.getOrDefault(artifact.slotKey(), Set.of()).contains(artifact.mainStatKey());
    }

    public boolean matchesSet(ArtifactItem artifact) {
        return allSetRecipes().stream().flatMap(List::stream)
                .anyMatch(rule -> ArtifactSetEffectCatalog.equivalentSetKeys(rule).contains(artifact.setKey()));
    }

    public List<List<ArtifactSetRule>> allSetRecipes() {
        if (alternativeSetRecipes.isEmpty()) return List.of(sets);
        java.util.ArrayList<List<ArtifactSetRule>> recipes = new java.util.ArrayList<>();
        recipes.add(sets);
        recipes.addAll(alternativeSetRecipes);
        return List.copyOf(recipes);
    }

    public ArtifactBuild withName(String localizedName) {
        return new ArtifactBuild(
                id, localizedName, characterKey, sets, alternativeSetRecipes,
                mainStatsBySlot, substatWeights, analysisEnabled, nativeSyncEnabled,
                quickEquipSyncEnabled, sourceVersion);
    }

    public ArtifactBuild withActivation(boolean enabled) {
        return withStates(enabled, enabled);
    }

    public ArtifactBuild withStates(boolean analysisEnabled, boolean nativeSyncEnabled) {
        return withStates(analysisEnabled, nativeSyncEnabled, quickEquipSyncEnabled);
    }

    public ArtifactBuild withStates(
            boolean analysisEnabled,
            boolean nativeSyncEnabled,
            boolean quickEquipSyncEnabled) {
        return new ArtifactBuild(
            id, name, characterKey, sets, alternativeSetRecipes,
                mainStatsBySlot, substatWeights,
                analysisEnabled, nativeSyncEnabled, quickEquipSyncEnabled, sourceVersion);
    }

    private static List<ArtifactSetRule> normalizeRecipe(List<ArtifactSetRule> recipe) {
        if (recipe == null || recipe.isEmpty()) return List.of();
        if (recipe.size() > 2) throw new IllegalArgumentException("set recipe supports at most two sets");
        int pieces = recipe.size() == 1 ? 4 : 2;
        LinkedHashSet<String> setKeys = new LinkedHashSet<>();
        ArrayList<ArtifactSetRule> normalized = new ArrayList<>();
        for (ArtifactSetRule rule : recipe) {
            String setKey = rule.setKey();
            if (setKeys.contains(setKey)) {
                setKey = ArtifactSetEffectCatalog.equivalentSetKeys(new ArtifactSetRule(setKey, 2)).stream()
                        .filter(candidate -> !setKeys.contains(candidate))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("set recipe cannot repeat a set"));
            }
            setKeys.add(setKey);
            normalized.add(new ArtifactSetRule(setKey, pieces));
        }
        return List.copyOf(normalized);
    }
}
