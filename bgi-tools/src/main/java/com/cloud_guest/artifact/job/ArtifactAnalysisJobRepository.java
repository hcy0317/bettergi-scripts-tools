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

    default List<ArtifactAnalysisJob> findReviewableByUid(String uid) {
        return findByUid(uid).stream()
                .filter(job -> job.snapshot() != null && job.analysisResult() != null)
                .filter(job -> job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                        || job.status() == ArtifactAnalysisJobStatus.APPROVED
                        || job.status() == ArtifactAnalysisJobStatus.RESCAN_REQUIRED)
                .toList();
    }

    default List<ArtifactAnalysisJob> findNonTerminalLockExecutions(String uid) {
        return findByUid(uid).stream()
                .filter(job -> job.operation()
                        == com.cloud_guest.artifact.launch.ArtifactLaunchOperation.EXECUTE_LOCK_PLAN)
                .filter(job -> job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                        || job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE)
                .toList();
    }

    default List<ArtifactAnalysisJobSummary> findActiveLockExecutionSummaries() {
        return List.of();
    }

    boolean delete(String uid, String id);
}
