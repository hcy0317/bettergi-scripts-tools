package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        return repository.save(build);
    }

    public List<ArtifactBuild> importAll(List<ArtifactBuild> builds) {
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
        return repository.delete(id);
    }
}
