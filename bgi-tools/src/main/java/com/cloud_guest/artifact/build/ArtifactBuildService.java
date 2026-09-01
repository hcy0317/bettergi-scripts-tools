package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSetEffectCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArtifactBuildService {
    private final ArtifactBuildRepository repository;

    public ArtifactBuildService(ArtifactBuildRepository repository) {
        this.repository = repository;
    }

    public List<ArtifactBuild> list() {
        return repository.findAll();
    }

    public ArtifactBuild save(String id, ArtifactBuild build) {
        if (!id.equals(build.id())) throw new IllegalArgumentException("build path id does not match payload id");
        validateSelections(replacing(repository.findAll(), build));
        return repository.save(build);
    }

    public ArtifactBuild updateState(
            String id,
            ArtifactBuildStateUpdateRequest request) {
        ArtifactBuild build = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("artifact build not found"));
        ArtifactBuild updated = switch (request.field()) {
            case "analysisEnabled" -> build.withStates(
                    request.enabled(), build.nativeSyncEnabled());
            case "nativeSyncEnabled" -> build.withStates(
                    build.analysisEnabled(), request.enabled());
            case "quickEquipPresetIndex" -> build.withQuickEquipPresetIndex(
                    request.presetIndex());
            default -> throw new IllegalArgumentException("unsupported artifact build state field");
        };
        validateSelections(replacing(repository.findAll(), updated));
        return repository.save(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ArtifactBuild> importAll(List<ArtifactBuild> builds) {
        Map<String, ArtifactBuild> merged = new LinkedHashMap<>();
        repository.findAll().forEach(build -> merged.put(build.id(), build));
        builds.forEach(build -> merged.put(build.id(), build));
        validateSelections(merged.values().stream().toList());
        builds.forEach(repository::save);
        return repository.findAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ArtifactBuild> updateBulkState(ArtifactBuildBulkStateRequest request) {
        List<ArtifactBuild> updated = repository.findAll().stream()
                .map(build -> {
                    if (!matchesScope(build, request.scope())) return build;
                    return "analysisEnabled".equals(request.field())
                            ? build.withStates(request.enabled(), build.nativeSyncEnabled())
                            : build.withStates(build.analysisEnabled(), request.enabled());
                })
                .toList();
        validateSelections(updated);
        updated.forEach(repository::save);
        return repository.findAll();
    }

    private static boolean matchesScope(ArtifactBuild build, String scope) {
        if ("all".equals(scope)) return true;
        boolean upstream = build.sourceVersion().startsWith("genshin-artifact-analyzer@");
        return "upstream".equals(scope) ? upstream : !upstream;
    }

    public List<ArtifactBuild> synchronizePresets(List<ArtifactBuild> presets) {
        Map<String, ArtifactBuild> existingById = repository.findAll().stream()
                .collect(Collectors.toMap(ArtifactBuild::id, Function.identity()));
        if (existingById.isEmpty()) {
            List<ArtifactBuild> normalized = normalizeNativeLockSelections(presets);
            validateSelections(normalized);
            normalized.forEach(repository::save);
            return repository.findAll();
        }
        for (ArtifactBuild preset : presets) {
            ArtifactBuild existing = existingById.get(preset.id());
            if (existing != null
                    && existing.sourceVersion().startsWith("genshin-artifact-analyzer@")
                    && !existing.name().equals(preset.name())) {
                repository.save(existing.withName(preset.name()));
            }
        }
        normalizeNativeLockSelections(repository.findAll()).forEach(repository::save);
        return repository.findAll();
    }

    public boolean delete(String id) {
        ArtifactBuild existing = repository.findById(id).orElse(null);
        if (existing == null) return false;
        if (existing.sourceVersion().startsWith("genshin-artifact-analyzer@")) {
            throw new IllegalStateException("bundled artifact presets cannot be deleted");
        }
        return repository.delete(id);
    }

    private static List<ArtifactBuild> replacing(
            List<ArtifactBuild> current,
            ArtifactBuild replacement) {
        Map<String, ArtifactBuild> merged = new LinkedHashMap<>();
        current.forEach(build -> merged.put(build.id(), build));
        merged.put(replacement.id(), replacement);
        return List.copyOf(merged.values());
    }

    private static void validateSelections(List<ArtifactBuild> builds) {
        validateQuickEquipSlots(builds);
        validateNativeLockCapacity(builds);
    }

    private static void validateQuickEquipSlots(List<ArtifactBuild> builds) {
        Set<String> selectedSlots = new LinkedHashSet<>();
        for (ArtifactBuild build : builds) {
            if (!build.quickEquipSyncEnabled()) continue;
            if (build.characterKey().isBlank()) {
                throw new IllegalStateException(
                        "quick-equip sync requires a character key");
            }
            String slot = build.characterKey() + ":" + build.quickEquipPresetIndex();
            if (!selectedSlots.add(slot)) {
                throw new IllegalStateException(
                        "quick-equip preset slot is already selected: " + slot);
            }
        }
    }

    private static void validateNativeLockCapacity(List<ArtifactBuild> builds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ArtifactBuild build : builds.stream()
                .filter(ArtifactBuild::nativeSyncEnabled)
                .sorted(java.util.Comparator.comparing(ArtifactBuild::id))
                .toList()) {
            for (String setKey : nativeSetKeys(build)) {
                int next = counts.getOrDefault(setKey, 0) + 1;
                if (next > 3) {
                    throw new IllegalStateException(
                            "artifact set supports at most three native builds: " + setKey);
                }
                counts.put(setKey, next);
            }
        }
    }

    private static List<ArtifactBuild> normalizeNativeLockSelections(
            List<ArtifactBuild> builds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ArtifactBuild> normalized = new ArrayList<>();
        for (ArtifactBuild build : builds.stream()
                .sorted(java.util.Comparator.comparing(ArtifactBuild::id))
                .toList()) {
            if (!build.nativeSyncEnabled()) {
                normalized.add(build);
                continue;
            }
            Set<String> setKeys = nativeSetKeys(build);
            boolean exceedsCapacity = setKeys.stream()
                    .anyMatch(setKey -> counts.getOrDefault(setKey, 0) >= 3);
            if (exceedsCapacity) {
                normalized.add(build.withStates(build.analysisEnabled(), false));
                continue;
            }
            setKeys.forEach(setKey -> counts.merge(setKey, 1, Integer::sum));
            normalized.add(build);
        }
        return List.copyOf(normalized);
    }

    private static Set<String> nativeSetKeys(ArtifactBuild build) {
        return build.allSetRecipes().stream()
                .flatMap(List::stream)
                .flatMap(rule -> ArtifactSetEffectCatalog.equivalentSetKeys(rule).stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
