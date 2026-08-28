package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;

import java.util.List;
import java.util.Optional;

public interface ArtifactBuildRepository {
    List<ArtifactBuild> findAll();

    Optional<ArtifactBuild> findById(String id);

    ArtifactBuild save(ArtifactBuild build);

    boolean delete(String id);
}
