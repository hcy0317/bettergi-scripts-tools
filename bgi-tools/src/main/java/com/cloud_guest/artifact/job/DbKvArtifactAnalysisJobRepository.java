package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.persistence.ArtifactJsonStore;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class DbKvArtifactAnalysisJobRepository implements ArtifactAnalysisJobRepository {
    private static final String TYPE = "artifact-analysis-job";
    private static final String SUMMARY_TYPE = "artifact-analysis-job-summary";
    private static final String ACTIVE_LOCK_TYPE = "artifact-analysis-active-lock-job";
    private static final String SUMMARY_MIGRATION_TYPE = "artifact-analysis-job-summary-migration";
    private static final String SUMMARY_MIGRATION_CURSOR_TYPE =
            "artifact-analysis-job-summary-migration-cursor";
    private final ArtifactJsonStore store;

    public DbKvArtifactAnalysisJobRepository(ArtifactJsonStore store) {
        this.store = store;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArtifactAnalysisJob save(ArtifactAnalysisJob job) {
        store.put(TYPE, key(job.uid(), job.id()), job);
        ArtifactAnalysisJobSummary summary = ArtifactAnalysisJobSummary.from(job);
        store.put(SUMMARY_TYPE, key(job.uid(), job.id()), summary);
        boolean activeLock = job.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && (job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                    || job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                    || job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE);
        if (activeLock) store.put(ACTIVE_LOCK_TYPE, key(job.uid(), job.id()), summary);
        else store.delete(ACTIVE_LOCK_TYPE, key(job.uid(), job.id()));
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
        migrateSummaryPage(uid, limit);
        return store.listByKeyPrefixLimited(
                SUMMARY_TYPE, uid + ":", ArtifactAnalysisJobSummary.class, limit);
    }

    private void migrateSummaryPage(String uid, int requestedLimit) {
        boolean complete = store.get(
                SUMMARY_MIGRATION_TYPE, uid, Boolean.class).orElse(false);
        int migratedThisCall = 0;
        while (!complete && migratedThisCall < requestedLimit) {
            long cursor = store.get(
                    SUMMARY_MIGRATION_CURSOR_TYPE, uid, Long.class)
                    .orElse(Long.MAX_VALUE);
            int batchSize = Math.min(100, requestedLimit - migratedThisCall);
            List<ArtifactJsonStore.StoredValue<ArtifactAnalysisJob>> batch =
                    store.listByKeyPrefixBeforeId(
                    TYPE, uid + ":", ArtifactAnalysisJob.class,
                    batchSize, cursor);
            batch.stream().map(ArtifactJsonStore.StoredValue::value).forEach(job -> store.put(
                    SUMMARY_TYPE,
                    key(job.uid(), job.id()),
                    ArtifactAnalysisJobSummary.from(job)));
            long nextCursor = batch.isEmpty()
                    ? cursor
                    : batch.getLast().id();
            store.put(SUMMARY_MIGRATION_CURSOR_TYPE, uid, nextCursor);
            migratedThisCall += batch.size();
            complete = batch.size() < batchSize;
            if (complete) store.put(SUMMARY_MIGRATION_TYPE, uid, true);
            if (batch.isEmpty()) break;
        }
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
        return store.list(ACTIVE_LOCK_TYPE, ArtifactAnalysisJobSummary.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String uid, String id) {
        boolean deleted = store.delete(TYPE, key(uid, id));
        store.delete(SUMMARY_TYPE, key(uid, id));
        store.delete(ACTIVE_LOCK_TYPE, key(uid, id));
        return deleted;
    }

    private static String key(String uid, String id) {
        return uid + ":" + id;
    }
}
