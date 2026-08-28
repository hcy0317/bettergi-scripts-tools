package com.cloud_guest.artifact.job;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryArtifactAnalysisJobRepository implements ArtifactAnalysisJobRepository {
    private final ConcurrentMap<String, ArtifactAnalysisJob> jobs = new ConcurrentHashMap<>();

    @Override
    public ArtifactAnalysisJob save(ArtifactAnalysisJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<ArtifactAnalysisJob> findById(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<ArtifactAnalysisJob> findByUid(String uid) {
        return jobs.values().stream()
                .filter(job -> job.uid().equals(uid))
                .sorted(Comparator.comparing(ArtifactAnalysisJob::createdAtUtc).reversed())
                .toList();
    }

    @Override
    public List<ArtifactAnalysisJobSummary> findSummariesByUid(
            String uid,
            int limit) {
        return findByUid(uid).stream()
                .limit(limit)
                .map(ArtifactAnalysisJobSummary::from)
                .toList();
    }

    @Override
    public boolean delete(String uid, String id) {
        ArtifactAnalysisJob job = jobs.get(id);
        return job != null && job.uid().equals(uid) && jobs.remove(id, job);
    }
}
