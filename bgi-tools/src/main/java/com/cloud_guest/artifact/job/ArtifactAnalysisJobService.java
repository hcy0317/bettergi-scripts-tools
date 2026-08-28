package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisEngine;
import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import com.cloud_guest.artifact.analysis.ArtifactAnalysisResult;
import com.cloud_guest.artifact.analysis.ArtifactDecision;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.execution.ArtifactDecisionPlan;
import com.cloud_guest.artifact.execution.ArtifactExecutionObservation;
import com.cloud_guest.artifact.execution.ArtifactExecutionGuard;
import com.cloud_guest.artifact.execution.ArtifactExecutionPreflight;
import com.cloud_guest.artifact.execution.ArtifactExecutionPreflightStatus;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.launch.ArtifactLaunchResult;
import com.cloud_guest.artifact.launch.ArtifactLaunchTarget;
import com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncPlan;

import java.time.Clock;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ArtifactAnalysisJobService {
    private final ArtifactAnalysisJobRepository repository;
    private final ArtifactAnalysisEngine analysisEngine;
    private final ArtifactExecutionGuard executionGuard;
    private final ArtifactLaunchRequestService launchRequestService;
    private final Clock clock;

    public ArtifactAnalysisJobService(
            ArtifactAnalysisJobRepository repository,
            ArtifactAnalysisEngine analysisEngine,
            ArtifactExecutionGuard executionGuard,
            ArtifactLaunchRequestService launchRequestService,
            Clock clock) {
        this.repository = repository;
        this.analysisEngine = analysisEngine;
        this.executionGuard = executionGuard;
        this.launchRequestService = launchRequestService;
        this.clock = clock;
    }

    public ArtifactJobStartResponse start(String uid, ArtifactLaunchOperation operation) {
        String now = clock.instant().toString();
        ArtifactAnalysisJob job = new ArtifactAnalysisJob(
                UUID.randomUUID().toString(), uid, operation,
                ArtifactAnalysisJobStatus.WAITING_FOR_HOST,
                null, null, null, now, now, null);
        repository.save(job);
        try {
            ArtifactLaunchResult launch = launchRequestService.create(uid, job.id(), operation);
            return new ArtifactJobStartResponse(job, launch);
        } catch (RuntimeException exception) {
            repository.delete(uid, job.id());
            throw exception;
        }
    }

    public ArtifactJobStartResponse startCharacterRoster(
            String uid,
            ArtifactBuildAutoActivationSettings settings,
            String gameNickname,
            String miliastraNickname,
            String miliastraCharacterKey) {
        String now = clock.instant().toString();
        ArtifactAnalysisJob job = new ArtifactAnalysisJob(
                UUID.randomUUID().toString(), uid, ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER,
                ArtifactAnalysisJobStatus.WAITING_FOR_HOST,
                null, null, null, now, now, null);
        repository.save(job);
        try {
            ArtifactLaunchResult launch = launchRequestService.createCharacterRoster(
                    uid, job.id(), settings, gameNickname, miliastraNickname,
                    miliastraCharacterKey);
            return new ArtifactJobStartResponse(job, launch);
        } catch (RuntimeException exception) {
            repository.delete(uid, job.id());
            throw exception;
        }
    }

    public ArtifactJobStartResponse startNative(
            String uid,
            ArtifactNativeSyncPlan plan) {
        if (plan.status() != com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncStatus.READY
                || plan.planDigest() == null || plan.planDigest().isBlank()) {
            throw new IllegalStateException("native artifact plan is not ready for execution");
        }
        String now = clock.instant().toString();
        ArtifactAnalysisJob job = new ArtifactAnalysisJob(
                UUID.randomUUID().toString(), uid, ArtifactLaunchOperation.REBUILD_NATIVE_PLANS,
                ArtifactAnalysisJobStatus.WAITING_FOR_HOST,
                null, null, null, now, now, null);
        repository.save(job);
        try {
            ArtifactLaunchResult launch = launchRequestService.create(
                    uid, job.id(), ArtifactLaunchOperation.REBUILD_NATIVE_PLANS,
                    null, List.of(), plan.capacity(), plan.planDigest());
            return new ArtifactJobStartResponse(job, launch);
        } catch (RuntimeException exception) {
            repository.delete(uid, job.id());
            throw exception;
        }
    }

    public ArtifactAnalysisJob submitSnapshot(
            String jobId,
            ArtifactSnapshot snapshot,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        ArtifactAnalysisJob job = require(jobId);
        if (!job.uid().equals(snapshot.uid())) throw new IllegalStateException("snapshot uid does not match job");
        if (job.status() != ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                && job.status() != ArtifactAnalysisJobStatus.HOST_CLAIMED
                && job.status() != ArtifactAnalysisJobStatus.RESCAN_REQUIRED) {
            throw new IllegalStateException("job is not waiting for a snapshot");
        }
        ArtifactAnalysisResult result = analysisEngine.analyze(snapshot, builds, policy);
        ArtifactDecisionPlan plan = new ArtifactDecisionPlan(
                UUID.randomUUID().toString(), snapshot.uid(), snapshot.artifactCount(),
                snapshot.snapshotDigest(), false, result.decisions());
        return repository.save(copy(
                job, ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                snapshot, result, plan, null));
    }

    public ArtifactAnalysisJob approve(String jobId, String snapshotDigest) {
        ArtifactAnalysisJob job = require(jobId);
        requireCurrentScoringPolicy(job);
        if (job.status() != ArtifactAnalysisJobStatus.READY_FOR_REVIEW || job.decisionPlan() == null) {
            throw new IllegalStateException("job is not ready for review");
        }
        if (!job.decisionPlan().sourceSnapshotDigest().equals(snapshotDigest)) {
            throw new IllegalStateException("snapshot digest does not match review result");
        }
        ArtifactDecisionPlan approved = new ArtifactDecisionPlan(
                job.decisionPlan().planId(), job.decisionPlan().uid(),
                job.decisionPlan().sourceArtifactCount(), job.decisionPlan().sourceSnapshotDigest(),
                true, job.decisionPlan().decisions());
        return repository.save(copy(
                job, ArtifactAnalysisJobStatus.APPROVED,
                job.snapshot(), job.analysisResult(), approved, null));
    }

    public synchronized ArtifactAnalysisJob claim(
            String jobId,
            String uid,
            ArtifactLaunchOperation operation) {
        ArtifactAnalysisJob job = require(jobId);
        boolean operationMatches = job.operation() == operation
                || (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.operation() == ArtifactLaunchOperation.ANALYZE);
        if (!job.uid().equals(uid) || !operationMatches) {
            throw new IllegalStateException("BetterGI 领取信息与任务不一致");
        }
        boolean claimable = job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                || (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.status() == ArtifactAnalysisJobStatus.APPROVED);
        if (!claimable) throw new IllegalStateException("任务当前不能被 BetterGI 领取");
        return repository.save(copy(
                job, ArtifactAnalysisJobStatus.HOST_CLAIMED,
                job.snapshot(), job.analysisResult(), job.decisionPlan(), null));
    }

    public synchronized ArtifactJobPreflightResponse preflight(
            String jobId,
            ArtifactExecutionObservation observation,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        ArtifactAnalysisJob job = require(jobId);
        if ((job.status() != ArtifactAnalysisJobStatus.APPROVED
                && job.status() != ArtifactAnalysisJobStatus.HOST_CLAIMED)
                || job.decisionPlan() == null) {
            throw new IllegalStateException("job does not have an approved plan");
        }
        if (job.decisionPlan().sourceArtifactCount() != observation.artifactCount()) {
            ArtifactSnapshot rescan = observation.fullSnapshot();
            if (rescan == null) {
                throw new IllegalStateException("artifact count changed without a complete rescan snapshot");
            }
            ArtifactAnalysisResult result = analysisEngine.analyze(rescan, builds, policy);
            ArtifactDecisionPlan replacement = new ArtifactDecisionPlan(
                    UUID.randomUUID().toString(), rescan.uid(), rescan.artifactCount(),
                    rescan.snapshotDigest(), false, result.decisions());
            ArtifactExecutionPreflight preflight = new ArtifactExecutionPreflight(
                    ArtifactExecutionPreflightStatus.RESCAN_REQUIRED, List.of(),
                    List.of("artifact count changed; the replacement snapshot is ready for review"));
            ArtifactAnalysisJob reviewed = repository.save(copy(
                    job, ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                    rescan, result, replacement, String.join("; ", preflight.reasons())));
            return new ArtifactJobPreflightResponse(reviewed, preflight);
        }

        ArtifactExecutionPreflight preflight = executionGuard.preflight(
                job.decisionPlan(), observation);
        ArtifactAnalysisJobStatus status;
        ArtifactDecisionPlan plan = job.decisionPlan();
        if (preflight.status() == ArtifactExecutionPreflightStatus.READY) {
            status = ArtifactAnalysisJobStatus.READY_TO_EXECUTE;
        } else {
            status = ArtifactAnalysisJobStatus.STALE_ABORT;
        }
        ArtifactAnalysisJob updated = repository.save(copy(
                job, status, job.snapshot(), job.analysisResult(), plan,
                preflight.reasons().isEmpty() ? null : String.join("; ", preflight.reasons())));
        return new ArtifactJobPreflightResponse(updated, preflight);
    }

    public ArtifactJobStartResponse launch(String jobId, ArtifactLaunchOperation operation) {
        return launch(jobId, operation, null);
    }

    public synchronized ArtifactJobStartResponse launch(
            String jobId,
            ArtifactLaunchOperation operation,
            List<Integer> requestedScanIndices) {
        ArtifactAnalysisJob sourceJob = require(jobId);
        if (operation != ArtifactLaunchOperation.EXECUTE_LOCK_PLAN) {
            throw new IllegalArgumentException("only lock-plan execution can be launched from an existing analysis");
        }
        if (sourceJob.decisionPlan() == null || !sourceJob.decisionPlan().approved()) {
            throw new IllegalStateException("artifact analysis must have an approved plan before execution");
        }
        requireCurrentScoringPolicy(sourceJob);
        boolean activeAttemptExists = repository.findByUid(sourceJob.uid()).stream()
                .filter(candidate -> candidate.decisionPlan() != null)
                .filter(candidate -> sourceJob.decisionPlan().planId()
                        .equals(candidate.decisionPlan().planId()))
                .anyMatch(candidate -> candidate.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                        || candidate.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || candidate.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE);
        if (activeAttemptExists) {
            throw new IllegalStateException("artifact lock plan is already being executed");
        }

        List<ArtifactDecision> changingDecisions = sourceJob.decisionPlan().decisions().stream()
                .filter(decision -> decision.expectedLocked() != decision.desiredLocked())
                .toList();
        List<ArtifactDecision> executionDecisions = changingDecisions;
        if (requestedScanIndices != null) {
            if (requestedScanIndices.isEmpty()) {
                throw new IllegalArgumentException("at least one filtered lock target is required");
            }
            Set<Integer> requested = new HashSet<>(requestedScanIndices);
            if (requested.size() != requestedScanIndices.size()) {
                throw new IllegalArgumentException("filtered lock targets contain duplicate scan indices");
            }
            Map<Integer, ArtifactDecision> approvedByIndex = sourceJob.decisionPlan().decisions().stream()
                    .collect(Collectors.toMap(ArtifactDecision::scanIndex, Function.identity()));
            for (Integer scanIndex : requested) {
                ArtifactDecision approved = approvedByIndex.get(scanIndex);
                if (approved == null) {
                    throw new IllegalArgumentException("filtered lock target is not part of the approved plan");
                }
                if (approved.expectedLocked() == approved.desiredLocked()) {
                    throw new IllegalArgumentException("filtered lock target does not require a state change");
                }
            }
            executionDecisions = changingDecisions.stream()
                    .filter(decision -> requested.contains(decision.scanIndex()))
                    .toList();
        }
        List<ArtifactLaunchTarget> targets = executionDecisions.stream()
                .map(decision -> new ArtifactLaunchTarget(
                        decision.scanIndex(), decision.expectedFingerprint(), decision.expectedLocked()))
                .toList();
        ArtifactDecisionPlan executionPlan = new ArtifactDecisionPlan(
                sourceJob.decisionPlan().planId(),
                sourceJob.decisionPlan().uid(),
                sourceJob.decisionPlan().sourceArtifactCount(),
                sourceJob.decisionPlan().sourceSnapshotDigest(),
                true,
                executionDecisions);
        String now = clock.instant().toString();
        ArtifactAnalysisJob attempt = new ArtifactAnalysisJob(
                UUID.randomUUID().toString(), sourceJob.uid(), operation,
                ArtifactAnalysisJobStatus.WAITING_FOR_HOST,
                sourceJob.snapshot(), null, executionPlan, now, now, null);
        repository.save(attempt);
        try {
            ArtifactLaunchResult launch = launchRequestService.create(
                    attempt.uid(), attempt.id(), operation,
                    sourceJob.decisionPlan().sourceArtifactCount(), targets, null, null);
            return new ArtifactJobStartResponse(attempt, launch);
        } catch (RuntimeException exception) {
            repository.delete(attempt.uid(), attempt.id());
            throw exception;
        }
    }

    public ArtifactAnalysisJob get(String jobId) {
        return presentPolicyStatus(require(jobId));
    }

    public ArtifactAnalysisJob complete(
            String jobId,
            ArtifactLaunchOperation operation,
            boolean success,
            String message) {
        ArtifactAnalysisJob job = require(jobId);
        if (job.status() == ArtifactAnalysisJobStatus.COMPLETED
                || job.status() == ArtifactAnalysisJobStatus.FAILED) {
            boolean sameOutcome = success == (job.status() == ArtifactAnalysisJobStatus.COMPLETED);
            if (sameOutcome) return job;
            throw new IllegalStateException("host completion conflicts with the terminal job state");
        }
        String diagnostic = message == null || message.isBlank()
                ? null
                : message.substring(0, Math.min(message.length(), 500));
        if (!success) {
            return repository.save(copy(
                    job, ArtifactAnalysisJobStatus.FAILED,
                    job.snapshot(), job.analysisResult(), job.decisionPlan(), diagnostic));
        }
        if (operation == ArtifactLaunchOperation.ANALYZE
                || (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW)) {
            return job;
        }
        return repository.save(copy(
                job, ArtifactAnalysisJobStatus.COMPLETED,
                job.snapshot(), job.analysisResult(), job.decisionPlan(), null));
    }

    public List<ArtifactAnalysisJob> list(String uid) {
        return repository.findByUid(uid).stream()
                .sorted(Comparator
                        .comparing(ArtifactAnalysisJob::createdAtUtc)
                        .thenComparing(ArtifactAnalysisJob::id)
                        .reversed())
                .map(this::presentPolicyStatus)
                .toList();
    }

    public List<ArtifactAnalysisJobSummary> listSummaries(String uid) {
        return list(uid).stream().map(ArtifactAnalysisJobSummary::from).toList();
    }

    public synchronized List<ArtifactAnalysisJob> reanalyzeReviewable(
            String uid,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        requireNoActiveLockExecution(uid);
        invalidateWaitingLockExecutions(uid);
        return reanalyzeReviewableInternal(uid, builds, policy);
    }

    public synchronized <T> T mutateAnalysisConfigurationAndReanalyze(
            String uid,
            Supplier<T> mutation,
            Supplier<List<ArtifactBuild>> builds,
            Supplier<ArtifactAnalysisPolicy> policy) {
        requireNoActiveLockExecution(uid);
        T result = mutation.get();
        invalidateWaitingLockExecutions(uid);
        reanalyzeReviewableInternal(uid, builds.get(), policy.get());
        return result;
    }

    private List<ArtifactAnalysisJob> reanalyzeReviewableInternal(
            String uid,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        return repository.findByUid(uid).stream()
                .filter(job -> job.operation() == ArtifactLaunchOperation.ANALYZE)
                .filter(job -> job.snapshot() != null && job.analysisResult() != null)
                .filter(job -> job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                        || job.status() == ArtifactAnalysisJobStatus.APPROVED
                        || job.status() == ArtifactAnalysisJobStatus.RESCAN_REQUIRED)
                .map(job -> {
                    ArtifactAnalysisResult result = analysisEngine.analyze(
                            job.snapshot(), builds, policy);
                    ArtifactDecisionPlan replacement = new ArtifactDecisionPlan(
                            UUID.randomUUID().toString(), job.snapshot().uid(),
                            job.snapshot().artifactCount(), job.snapshot().snapshotDigest(),
                            false, result.decisions());
                    return repository.save(copy(
                            job, ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                            job.snapshot(), result, replacement, null));
                })
                .toList();
    }

    private void requireNoActiveLockExecution(String uid) {
        boolean hostOwnsExecution = repository.findByUid(uid).stream()
                .filter(job -> job.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN)
                .anyMatch(job -> job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE);
        if (hostOwnsExecution) {
            throw new IllegalStateException(
                    "BetterGI 正在执行该 UID 的锁定方案，完成或停止后才能修改 Build");
        }
    }

    private void invalidateWaitingLockExecutions(String uid) {
        repository.findByUid(uid).stream()
                .filter(job -> job.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN)
                .filter(job -> job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST)
                .forEach(job -> {
                    launchRequestService.revokeForJob(job.uid(), job.id());
                    repository.save(copy(
                            job, ArtifactAnalysisJobStatus.STALE_ABORT,
                            job.snapshot(), job.analysisResult(), job.decisionPlan(),
                            "Build 或评分设置已变化，待执行的旧锁定方案已撤销"));
                });
    }

    public boolean delete(String jobId) {
        ArtifactAnalysisJob job = require(jobId);
        if (job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE
                || job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED) {
            throw new IllegalStateException("任务正在执行，当前不能删除");
        }
        launchRequestService.revokeForJob(job.uid(), job.id());
        return repository.delete(job.uid(), job.id());
    }

    private ArtifactAnalysisJob require(String jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("artifact analysis job was not found"));
    }

    private static boolean usesCurrentScoringPolicy(ArtifactAnalysisJob job) {
        return job.analysisResult() != null
                && ArtifactAnalysisEngine.POLICY_VERSION.equals(job.analysisResult().policyVersion());
    }

    private static void requireCurrentScoringPolicy(ArtifactAnalysisJob job) {
        if (!usesCurrentScoringPolicy(job)) {
            throw new IllegalStateException(
                    "artifact analysis uses an outdated scoring policy; rescan is required");
        }
    }

    private ArtifactAnalysisJob presentPolicyStatus(ArtifactAnalysisJob job) {
        boolean reviewable = job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                || job.status() == ArtifactAnalysisJobStatus.APPROVED;
        if (!reviewable || usesCurrentScoringPolicy(job)) return job;
        return new ArtifactAnalysisJob(
                job.id(), job.uid(), job.operation(), ArtifactAnalysisJobStatus.RESCAN_REQUIRED,
                job.snapshot(), job.analysisResult(), job.decisionPlan(),
                job.createdAtUtc(), job.updatedAtUtc(), "评分算法已更新，请重新扫描后再审核。");
    }

    private ArtifactAnalysisJob copy(
            ArtifactAnalysisJob source,
            ArtifactAnalysisJobStatus status,
            ArtifactSnapshot snapshot,
            ArtifactAnalysisResult result,
            ArtifactDecisionPlan plan,
            String error) {
        return new ArtifactAnalysisJob(
                source.id(), source.uid(), source.operation(), status,
                snapshot, result, plan, source.createdAtUtc(), clock.instant().toString(), error);
    }
}
