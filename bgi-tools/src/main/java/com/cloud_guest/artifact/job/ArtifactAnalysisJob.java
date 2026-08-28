package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisResult;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.execution.ArtifactDecisionPlan;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;

public record ArtifactAnalysisJob(
        String id,
        String uid,
        ArtifactLaunchOperation operation,
        ArtifactAnalysisJobStatus status,
        ArtifactSnapshot snapshot,
        ArtifactAnalysisResult analysisResult,
        ArtifactDecisionPlan decisionPlan,
        String createdAtUtc,
        String updatedAtUtc,
        String errorMessage) {
}
