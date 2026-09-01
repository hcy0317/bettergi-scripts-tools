package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
        validateQuickEquipCapacity(replacing(repository.findAll(), build));
        return repository.save(build);
    }

    public ArtifactBuild updateState(
            String id,
            ArtifactBuildStateUpdateRequest request) {
        ArtifactBuild build = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("artifact build not found"));
        ArtifactBuild updated = switch (request.field()) {
            case "analysisEnabled" -> build.withStates(
                    request.enabled(), build.nativeSyncEnabled(), build.quickEquipSyncEnabled());
            case "nativeSyncEnabled" -> build.withStates(
                    build.analysisEnabled(), request.enabled(), build.quickEquipSyncEnabled());
            case "quickEquipSyncEnabled" -> build.withStates(
                    build.analysisEnabled(), build.nativeSyncEnabled(), request.enabled());
            default -> throw new IllegalArgumentException("unsupported artifact build state field");
        };
        validateQuickEquipCapacity(replacing(repository.findAll(), updated));
        return repository.save(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ArtifactBuild> importAll(List<ArtifactBuild> builds) {
        Map<String, ArtifactBuild> merged = new LinkedHashMap<>();
        repository.findAll().forEach(build -> merged.put(build.id(), build));
        builds.forEach(build -> merged.put(build.id(), build));
        validateQuickEquipCapacity(merged.values().stream().toList());
        builds.forEach(repository::save);
        return repository.findAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ArtifactBuild> updateBulkState(ArtifactBuildBulkStateRequest request) {
        for (ArtifactBuild build : repository.findAll()) {
            if (!matchesScope(build, request.scope())) continue;
            ArtifactBuild updated = "analysisEnabled".equals(request.field())
                    ? build.withStates(request.enabled(), build.nativeSyncEnabled())
                    : build.withStates(build.analysisEnabled(), request.enabled());
            repository.save(updated);
        }
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
            return importAll(presets);
        }
        for (ArtifactBuild preset : presets) {
            ArtifactBuild existing = existingById.get(preset.id());
            if (existing != null
                    && existing.sourceVersion().startsWith("genshin-artifact-analyzer@")
                    && !existing.name().equals(preset.name())) {
                repository.save(existing.withName(preset.name()));
            }
        }
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

    private static void validateQuickEquipCapacity(List<ArtifactBuild> builds) {
        Map<String, Integer> selectedByCharacter = new LinkedHashMap<>();
        for (ArtifactBuild build : builds) {
            if (!build.quickEquipSyncEnabled()) continue;
            if (build.characterKey().isBlank()) {
                throw new IllegalStateException(
                        "quick-equip sync requires a character key");
            }
            selectedByCharacter.merge(build.characterKey(), 1, Integer::sum);
        }
        selectedByCharacter.forEach((characterKey, count) -> {
            if (count > 2) {
                throw new IllegalStateException(
                        "character supports at most two quick-equip builds: " + characterKey);
            }
        });
    }
}
