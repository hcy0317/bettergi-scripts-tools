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
import org.springframework.transaction.annotation.Transactional;

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

    public ArtifactAnalysisJob approve(
            String jobId,
            String snapshotDigest,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        ArtifactAnalysisJob job = require(jobId);
        requireCurrentAnalysisInputs(job, builds, policy);
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

    ArtifactAnalysisJob approve(String jobId, String snapshotDigest) {
        return approve(jobId, snapshotDigest, null, null);
    }

    public synchronized ArtifactAnalysisJob claim(
            String jobId,
            String uid,
            ArtifactLaunchOperation operation) {
        return claimInternal(jobId, uid, operation, null);
    }

    public synchronized ArtifactAnalysisJob claimAuthorized(
            String jobId,
            String uid,
            ArtifactLaunchOperation operation,
            Runnable authorize) {
        return claimInternal(jobId, uid, operation, authorize);
    }

    private ArtifactAnalysisJob claimInternal(
            String jobId,
            String uid,
            ArtifactLaunchOperation operation,
            Runnable authorize) {
        ArtifactAnalysisJob job = require(jobId);
        boolean operationMatches = job.operation() == operation
                || (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.operation() == ArtifactLaunchOperation.ANALYZE);
        if (!job.uid().equals(uid) || !operationMatches) {
            throw new IllegalStateException("BetterGI 领取信息与任务不一致");
        }
        if (job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED) {
            if (authorize != null) authorize.run();
            return job;
        }
        boolean resumablePhase = operation == ArtifactLaunchOperation.ANALYZE
                && job.operation() == ArtifactLaunchOperation.ANALYZE
                && job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                || operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.operation() == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE;
        if (resumablePhase) {
            if (authorize != null) authorize.run();
            return repository.save(copy(
                    job, ArtifactAnalysisJobStatus.HOST_CLAIMED,
                    job.snapshot(), job.analysisResult(), job.decisionPlan(), null));
        }
        boolean claimable = job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                || (operation == ArtifactLaunchOperation.EXECUTE_LOCK_PLAN
                && job.status() == ArtifactAnalysisJobStatus.APPROVED);
        if (!claimable) throw new IllegalStateException("任务当前不能被 BetterGI 领取");
        if (authorize != null) authorize.run();
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
        requireCurrentAnalysisInputs(job, builds, policy);
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

    ArtifactJobStartResponse launch(String jobId, ArtifactLaunchOperation operation) {
        return launch(jobId, operation, null, null, null);
    }

    ArtifactJobStartResponse launch(
            String jobId,
            ArtifactLaunchOperation operation,
            List<Integer> requestedScanIndices) {
        return launch(jobId, operation, requestedScanIndices, null, null);
    }

    public synchronized ArtifactJobStartResponse launch(
            String jobId,
            ArtifactLaunchOperation operation,
            List<Integer> requestedScanIndices,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        ArtifactAnalysisJob sourceJob = require(jobId);
        if (operation != ArtifactLaunchOperation.EXECUTE_LOCK_PLAN) {
            throw new IllegalArgumentException("only lock-plan execution can be launched from an existing analysis");
        }
        if (sourceJob.decisionPlan() == null || !sourceJob.decisionPlan().approved()) {
            throw new IllegalStateException("artifact analysis must have an approved plan before execution");
        }
        requireCurrentAnalysisInputs(sourceJob, builds, policy);
        boolean activeAttemptExists = repository.findByUid(sourceJob.uid()).stream()
                .filter(candidate -> candidate.decisionPlan() != null)
                .filter(candidate -> sourceJob.decisionPlan().planId()
                        .equals(candidate.decisionPlan().planId()))
                .anyMatch(candidate -> (candidate.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                        && (!launchRequestService.isExpired(
                                candidate.createdAtUtc(), clock.instant())
                            || launchRequestService.hasAcceptedRequest(
                                candidate.uid(), candidate.id())))
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
                sourceJob.snapshot(), sourceJob.analysisResult(), executionPlan,
                now, now, null);
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

    public ArtifactAnalysisJob get(
            String jobId,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        return presentPolicyStatus(
                require(jobId),
                ArtifactAnalysisEngine.analysisInputDigest(builds, policy));
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

    public List<ArtifactAnalysisJobSummary> listSummaries(
            String uid,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        String currentInputDigest = ArtifactAnalysisEngine.analysisInputDigest(
                builds, policy);
        return repository.findSummariesByUid(uid, 100).stream()
                .map(summary -> presentPolicyStatus(summary, currentInputDigest))
                .sorted(Comparator
                        .comparing(ArtifactAnalysisJobSummary::createdAtUtc)
                        .thenComparing(ArtifactAnalysisJobSummary::id)
                        .reversed())
                .toList();
    }

    public synchronized List<ArtifactAnalysisJob> reanalyzeReviewable(
            String uid,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        requireNoActiveLockExecution(uid);
        invalidateWaitingLockExecutions(uid);
        return reanalyzeReviewableInternal(uid, builds, policy);
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized <T> T mutateAnalysisConfigurationAndReanalyze(
            String uid,
            Supplier<T> mutation,
            Supplier<List<ArtifactBuild>> builds,
            Supplier<ArtifactAnalysisPolicy> policy) {
        requireNoActiveLockExecutionGlobally();
        T result = mutation.get();
        reanalyzeReviewableInternal(uid, builds.get(), policy.get());
        invalidateWaitingLockExecutionsGlobally();
        invalidateWaitingLockExecutions(uid);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized <T> T mutateUidActivationAndReanalyze(
            String uid,
            Supplier<T> mutation,
            Supplier<List<ArtifactBuild>> builds,
            Supplier<ArtifactAnalysisPolicy> policy) {
        requireNoActiveLockExecution(uid);
        T result = mutation.get();
        reanalyzeReviewableInternal(uid, builds.get(), policy.get());
        invalidateWaitingLockExecutions(uid);
        return result;
    }

    private List<ArtifactAnalysisJob> reanalyzeReviewableInternal(
            String uid,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        return repository.findReviewableByUid(uid).stream()
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
        boolean hostOwnsExecution = repository.findNonTerminalLockExecutions(uid).stream()
                .anyMatch(job -> job.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || job.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE);
        if (hostOwnsExecution) {
            throw new IllegalStateException(
                    "BetterGI 正在执行该 UID 的锁定方案，完成或停止后才能修改 Build");
        }
    }

    private void requireNoActiveLockExecutionGlobally() {
        boolean hostOwnsExecution = repository.findActiveLockExecutionSummaries().stream()
                .anyMatch(summary -> summary.status() == ArtifactAnalysisJobStatus.HOST_CLAIMED
                        || summary.status() == ArtifactAnalysisJobStatus.READY_TO_EXECUTE);
        if (hostOwnsExecution) {
            throw new IllegalStateException(
                    "BetterGI 正在执行锁定方案，全部账号完成或停止后才能修改全局 Build 或评分设置");
        }
    }

    private void invalidateWaitingLockExecutions(String uid) {
        repository.findNonTerminalLockExecutions(uid).stream()
                .filter(job -> job.status() == ArtifactAnalysisJobStatus.WAITING_FOR_HOST)
                .forEach(job -> {
                    repository.save(copy(
                            job, ArtifactAnalysisJobStatus.STALE_ABORT,
                            job.snapshot(), job.analysisResult(), job.decisionPlan(),
                            "Build 或评分设置已变化，待执行的旧锁定方案已撤销"));
                    try {
                        launchRequestService.revokeForJob(job.uid(), job.id());
                    } catch (RuntimeException ignored) {
                        // The stale DB state remains authoritative; an orphaned
                        // request can no longer be claimed or execute game input.
                    }
                });
    }

    private void invalidateWaitingLockExecutionsGlobally() {
        repository.findActiveLockExecutionSummaries().stream()
                .filter(summary -> summary.status()
                        == ArtifactAnalysisJobStatus.WAITING_FOR_HOST)
                .map(summary -> repository.findById(summary.id()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .forEach(job -> {
                    repository.save(copy(
                            job, ArtifactAnalysisJobStatus.STALE_ABORT,
                            job.snapshot(), job.analysisResult(), job.decisionPlan(),
                            "全局 Build 或评分设置已变化，待执行的旧锁定方案已撤销"));
                    try {
                        launchRequestService.revokeForJob(job.uid(), job.id());
                    } catch (RuntimeException ignored) {
                        // Fail closed on the persisted stale state.
                    }
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

    private static void requireCurrentAnalysisInputs(
            ArtifactAnalysisJob job,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        requireCurrentScoringPolicy(job);
        if (builds == null || policy == null) return;
        String currentDigest = ArtifactAnalysisEngine.analysisInputDigest(builds, policy);
        if (job.analysisResult() == null
                || !currentDigest.equals(job.analysisResult().analysisInputDigest())) {
            throw new IllegalStateException(
                    "artifact analysis uses outdated Build or scoring settings; reanalysis is required");
        }
    }

    private ArtifactAnalysisJob presentPolicyStatus(ArtifactAnalysisJob job) {
        return presentPolicyStatus(job, null);
    }

    private ArtifactAnalysisJob presentPolicyStatus(
            ArtifactAnalysisJob job,
            String currentInputDigest) {
        job = presentLaunchStatus(job);
        boolean reviewable = job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                || job.status() == ArtifactAnalysisJobStatus.APPROVED;
        boolean currentPolicy = usesCurrentScoringPolicy(job);
        boolean currentInputs = currentInputDigest == null
                || job.analysisResult() != null
                && currentInputDigest.equals(job.analysisResult().analysisInputDigest());
        if (!reviewable || currentPolicy && currentInputs) return job;
        return new ArtifactAnalysisJob(
                job.id(), job.uid(), job.operation(), ArtifactAnalysisJobStatus.RESCAN_REQUIRED,
                job.snapshot(), job.analysisResult(), job.decisionPlan(),
                job.createdAtUtc(), job.updatedAtUtc(),
                currentPolicy
                        ? "Build 或评分设置已更新，请重新计算后再审核。"
                        : "评分算法已更新，请重新扫描后再审核。");
    }

    private ArtifactAnalysisJobSummary presentPolicyStatus(
            ArtifactAnalysisJobSummary summary,
            String currentInputDigest) {
        summary = presentLaunchStatus(summary);
        boolean reviewable = summary.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW
                || summary.status() == ArtifactAnalysisJobStatus.APPROVED;
        if (!reviewable || summary.analysisResult() == null) {
            return summary;
        }
        boolean currentPolicy = ArtifactAnalysisEngine.POLICY_VERSION.equals(
                summary.analysisResult().policyVersion());
        boolean currentInputs = currentInputDigest.equals(
                summary.analysisResult().analysisInputDigest());
        if (currentPolicy && currentInputs) return summary;
        return new ArtifactAnalysisJobSummary(
                summary.id(), summary.uid(), summary.operation(),
                ArtifactAnalysisJobStatus.RESCAN_REQUIRED,
                summary.snapshot(), summary.analysisResult(), summary.decisionPlan(),
                summary.createdAtUtc(), summary.updatedAtUtc(),
                currentPolicy
                        ? "Build 或评分设置已更新，请重新计算后再审核。"
                        : "评分算法已更新，请重新扫描后再审核。");
    }

    private ArtifactAnalysisJob presentLaunchStatus(ArtifactAnalysisJob job) {
        if (job.status() != ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                || !launchRequestService.isExpired(job.createdAtUtc(), clock.instant())
                || launchRequestService.hasAcceptedRequest(job.uid(), job.id())) {
            return job;
        }
        return new ArtifactAnalysisJob(
                job.id(), job.uid(), job.operation(), ArtifactAnalysisJobStatus.FAILED,
                job.snapshot(), job.analysisResult(), job.decisionPlan(),
                job.createdAtUtc(), job.updatedAtUtc(),
                "连接 BetterGI 的请求已过期，请删除本次任务后重新发起。");
    }

    private ArtifactAnalysisJobSummary presentLaunchStatus(
            ArtifactAnalysisJobSummary summary) {
        if (summary.status() != ArtifactAnalysisJobStatus.WAITING_FOR_HOST
                || !launchRequestService.isExpired(summary.createdAtUtc(), clock.instant())
                || launchRequestService.hasAcceptedRequest(summary.uid(), summary.id())) {
            return summary;
        }
        return new ArtifactAnalysisJobSummary(
                summary.id(), summary.uid(), summary.operation(),
                ArtifactAnalysisJobStatus.FAILED,
                summary.snapshot(), summary.analysisResult(), summary.decisionPlan(),
                summary.createdAtUtc(), summary.updatedAtUtc(),
                "连接 BetterGI 的请求已过期，请删除本次任务后重新发起。");
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
