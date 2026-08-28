package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbKvArtifactBuildRepository implements ArtifactBuildRepository {
    private static final String TYPE = "artifact-build";
    private final ArtifactJsonStore store;

    public DbKvArtifactBuildRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public List<ArtifactBuild> findAll() {
        return store.list(TYPE, ArtifactBuild.class);
    }

    @Override
    public Optional<ArtifactBuild> findById(String id) {
        return store.get(TYPE, id, ArtifactBuild.class);
    }

    @Override
    public ArtifactBuild save(ArtifactBuild build) {
        return store.put(TYPE, build.id(), build);
    }

    @Override
    public boolean delete(String id) {
        return store.delete(TYPE, id);
    }
}
