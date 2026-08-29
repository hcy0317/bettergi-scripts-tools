package com.cloud_guest.artifact.job;

public enum ArtifactAnalysisJobStatus {
    WAITING_FOR_HOST,
    HOST_CLAIMED,
    READY_FOR_REVIEW,
    APPROVED,
    RESCAN_REQUIRED,
    READY_TO_EXECUTE,
    STALE_ABORT,
    COMPLETED,
    FAILED
}
