package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbKvArtifactAnalysisJobRepository implements ArtifactAnalysisJobRepository {
    private static final String TYPE = "artifact-analysis-job";
    private static final String SUMMARY_TYPE = "artifact-analysis-job-summary";
    private final ArtifactJsonStore store;

    public DbKvArtifactAnalysisJobRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    public ArtifactAnalysisJob save(ArtifactAnalysisJob job) {
        store.put(TYPE, key(job.uid(), job.id()), job);
        store.put(SUMMARY_TYPE, key(job.uid(), job.id()), ArtifactAnalysisJobSummary.from(job));
        return job;
    }

    @Override
    public Optional<ArtifactAnalysisJob> findById(String id) {
        return store.getByKeySuffix(TYPE, ":" + id, ArtifactAnalysisJob.class);
    }

    @Override
    public List<ArtifactAnalysisJob> findByUid(String uid) {
        return store.listByKeyPrefix(TYPE, uid + ":", ArtifactAnalysisJob.class);
    }

    @Override
    public List<ArtifactAnalysisJobSummary> findSummariesByUid(
            String uid,
            int limit) {
        List<ArtifactAnalysisJobSummary> summaries = store.listByKeyPrefixLimited(
                SUMMARY_TYPE, uid + ":", ArtifactAnalysisJobSummary.class, limit);
        if (!summaries.isEmpty()) return summaries;

        // Bounded one-time migration for installations created before summaries
        // were stored separately. Never deserialize unbounded historical snapshots.
        return store.listByKeyPrefixLimited(
                        TYPE, uid + ":", ArtifactAnalysisJob.class, limit)
                .stream()
                .map(job -> store.put(
                        SUMMARY_TYPE,
                        key(job.uid(), job.id()),
                        ArtifactAnalysisJobSummary.from(job)))
                .toList();
    }

    @Override
    public boolean delete(String uid, String id) {
        store.delete(SUMMARY_TYPE, key(uid, id));
        return store.delete(TYPE, key(uid, id));
    }

    private static String key(String uid, String id) {
        return uid + ":" + id;
    }
}
