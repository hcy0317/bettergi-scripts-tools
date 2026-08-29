package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryArtifactBuildRepository implements ArtifactBuildRepository {
    private final ConcurrentMap<String, ArtifactBuild> builds = new ConcurrentHashMap<>();

    @Override
    public List<ArtifactBuild> findAll() {
        return builds.values().stream().sorted(Comparator.comparing(ArtifactBuild::id)).toList();
    }

    @Override
    public Optional<ArtifactBuild> findById(String id) {
        return Optional.ofNullable(builds.get(id));
    }

    @Override
    public ArtifactBuild save(ArtifactBuild build) {
        builds.put(build.id(), build);
        return build;
    }

    @Override
    public boolean delete(String id) {
        return builds.remove(id) != null;
    }
}
