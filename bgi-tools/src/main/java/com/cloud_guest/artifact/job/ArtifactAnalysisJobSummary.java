package com.cloud_guest.artifact.job;

public record ArtifactAnalysisJobSummary(
        String id,
        String uid,
        com.cloud_guest.artifact.launch.ArtifactLaunchOperation operation,
        ArtifactAnalysisJobStatus status,
        ArtifactSnapshotSummary snapshot,
        ArtifactAnalysisResultSummary analysisResult,
        ArtifactDecisionPlanSummary decisionPlan,
        String createdAtUtc,
        String updatedAtUtc,
        String errorMessage) {

    public static ArtifactAnalysisJobSummary from(ArtifactAnalysisJob job) {
        ArtifactSnapshotSummary snapshot = job.snapshot() == null ? null : new ArtifactSnapshotSummary(
                job.snapshot().artifactCount(), job.snapshot().artifacts().size(),
                job.snapshot().snapshotDigest());
        ArtifactAnalysisResultSummary result = job.analysisResult() == null ? null
                : new ArtifactAnalysisResultSummary(
                        job.analysisResult().policyVersion(),
                        job.analysisResult().analysisInputDigest(),
                        job.analysisResult().summary());
        ArtifactDecisionPlanSummary plan = job.decisionPlan() == null ? null
                : new ArtifactDecisionPlanSummary(job.decisionPlan().approved());
        return new ArtifactAnalysisJobSummary(
                job.id(), job.uid(), job.operation(), job.status(), snapshot, result, plan,
                job.createdAtUtc(), job.updatedAtUtc(), job.errorMessage());
    }
}
