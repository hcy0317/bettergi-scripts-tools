package com.cloud_guest.artifact.job;

import java.util.List;
import java.util.Optional;

public interface ArtifactAnalysisJobRepository {
    ArtifactAnalysisJob save(ArtifactAnalysisJob job);

    Optional<ArtifactAnalysisJob> findById(String id);

    List<ArtifactAnalysisJob> findByUid(String uid);

    default List<ArtifactAnalysisJobSummary> findSummariesByUid(
            String uid,
            int limit) {
        return findByUid(uid).stream()
                .limit(limit)
                .map(ArtifactAnalysisJobSummary::from)
                .toList();
    }

    boolean delete(String uid, String id);
}
