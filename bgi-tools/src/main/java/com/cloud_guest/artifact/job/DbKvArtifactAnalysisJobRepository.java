package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbKvArtifactAnalysisJobRepository implements ArtifactAnalysisJobRepository {
    private static final String TYPE = "artifact-analysis-job";
    private static final String SUMMARY_TYPE = "artifact-analysis-job-summary";
    private static final String SUMMARY_MIGRATION_TYPE = "artifact-analysis-job-summary-migration";
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
        boolean migrationComplete = store.get(
                SUMMARY_MIGRATION_TYPE, uid, Boolean.class).orElse(false);
        if (migrationComplete) return summaries;

        List<ArtifactAnalysisJobSummary> migrated = store.listByKeyPrefixLimited(
                        TYPE, uid + ":", ArtifactAnalysisJob.class, limit)
                .stream()
                .map(job -> store.put(
                        SUMMARY_TYPE,
                        key(job.uid(), job.id()),
                        ArtifactAnalysisJobSummary.from(job)))
                .toList();
        store.put(SUMMARY_MIGRATION_TYPE, uid, true);
        return java.util.stream.Stream.concat(summaries.stream(), migrated.stream())
                .collect(java.util.stream.Collectors.toMap(
                        ArtifactAnalysisJobSummary::id,
                        java.util.function.Function.identity(),
                        (left, right) -> left))
                .values().stream()
                .sorted(java.util.Comparator.comparing(
                        ArtifactAnalysisJobSummary::createdAtUtc).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<ArtifactAnalysisJob> findReviewableByUid(String uid) {
        return findSummariesByUid(uid, 1000).stream()
                .filter(summary -> summary.snapshot() != null && summary.analysisResult() != null)
                .filter(summary -> summary.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                        || summary.status() == ArtifactAnalysisJobStatus.APPROVED
                        || summary.status() == ArtifactAnalysisJobStatus.RESCAN_REQUIRED)
                .map(summary -> findById(summary.id()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<ArtifactAnalysisJob> findNonTerminalLockExecutions(String uid) {
        return findSummariesByUid(uid, 1000).stream()
                .filter(summary -> summary.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN)
                .filter(summary -> summary.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                        || summary.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || summary.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE)
                .map(summary -> findById(summary.id()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<ArtifactAnalysisJobSummary> findActiveLockExecutionSummaries() {
        return store.listLimited(
                        SUMMARY_TYPE, ArtifactAnalysisJobSummary.class, 1000)
                .stream()
                .filter(summary -> summary.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN)
                .filter(summary -> summary.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || summary.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE)
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
