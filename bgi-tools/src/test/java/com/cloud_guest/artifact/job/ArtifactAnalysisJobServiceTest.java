package com.cloud_guest.artifact.job;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisEngine;
import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import com.cloud_guest.artifact.analysis.ArtifactAnalysisResult;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.domain.ArtifactSubstat;
import com.cloud_guest.artifact.execution.ArtifactExecutionGuard;
import com.cloud_guest.artifact.execution.ArtifactExecutionObservation;
import com.cloud_guest.artifact.execution.ArtifactExecutionPreflightStatus;
import com.cloud_guest.artifact.execution.ArtifactDecisionPlan;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequest;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.launch.ArtifactLaunchTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactAnalysisJobServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void listAlwaysReturnsNewestJobsFirstRegardlessOfRepositoryOrder() {
        ArtifactAnalysisJob older = job("job-a", "2026-08-27T00:00:00Z");
        ArtifactAnalysisJob newer = job("job-b", "2026-08-28T00:00:00Z");
        ArtifactAnalysisJobService service = new ArtifactAnalysisJobService(
                new FixedOrderRepository(List.of(older, newer)),
                new ArtifactAnalysisEngine(),
                new ArtifactExecutionGuard(),
                launchRequestService(),
                Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC));

        assertThat(service.list("102550550"))
                .extracting(ArtifactAnalysisJob::id)
                .containsExactly("job-b", "job-a");
    }

    @Test
    void listSummariesNeverSerializeArtifactOrBuildScoreMatrices() throws Exception {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob started = service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                started.id(), snapshot(List.of(item(0, false), item(1, true))),
                List.of(build()), ArtifactAnalysisPolicy.defaults());

        ArtifactAnalysisJobSummary summary = service.listSummaries(
                "102550550", List.of(build()), ArtifactAnalysisPolicy.defaults()).getFirst();
        String json = new ObjectMapper().writeValueAsString(summary);

        assertThat(summary.snapshot().artifactCount()).isEqualTo(2);
        assertThat(summary.snapshot().analyzableArtifactCount()).isEqualTo(2);
        assertThat(summary.analysisResult().summary().keep()).isGreaterThanOrEqualTo(0);
        assertThat(json).doesNotContain("artifacts", "decisions", "buildCurrentScores");
        assertThat(json.length()).isLessThan(2_000);
    }

    @Test
    void webStartedJobBecomesReviewableAndOnlyExactSnapshotCanBeApproved() {
        ArtifactAnalysisJobService service = service();

        ArtifactJobStartResponse start = service.start("102550550", ArtifactLaunchOperation.ANALYZE);
        assertThat(start.job().status()).isEqualTo(ArtifactAnalysisJobStatus.WAITING_FOR_HOST);
        assertThat(start.launch().launchUri()).startsWith("BetterGIArtifact://analysis?request=");

        ArtifactSnapshot snapshot = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob reviewed = service.submitSnapshot(
                start.job().id(), snapshot, List.of(build()), ArtifactAnalysisPolicy.defaults());
        assertThat(reviewed.status()).isEqualTo(ArtifactAnalysisJobStatus.READY_FOR_REVIEW);
        assertThat(reviewed.analysisResult().decisions()).hasSize(1);
        assertThat(reviewed.decisionPlan().approved()).isFalse();

        assertThatThrownBy(() -> service.approve(start.job().id(), "stale-digest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("digest");

        ArtifactAnalysisJob approved = service.approve(start.job().id(), snapshot.snapshotDigest());
        assertThat(approved.status()).isEqualTo(ArtifactAnalysisJobStatus.APPROVED);
        assertThat(approved.decisionPlan().approved()).isTrue();
    }

    @Test
    void changedCountInvalidatesApprovalAndRequiresRescan() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob job = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(job.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(job.id(), source.snapshotDigest());

        ArtifactSnapshot live = snapshot(List.of(item(0, false), item(1, false)));
        ArtifactJobPreflightResponse preflight = service.preflight(
                job.id(),
                new ArtifactExecutionObservation(
                        live.uid(), live.artifactCount(), live.artifacts(), live),
                List.of(build()), ArtifactAnalysisPolicy.defaults());

        assertThat(preflight.preflight().status())
                .isEqualTo(ArtifactExecutionPreflightStatus.RESCAN_REQUIRED);
        assertThat(preflight.job().status()).isEqualTo(ArtifactAnalysisJobStatus.READY_FOR_REVIEW);
        assertThat(preflight.job().decisionPlan().approved()).isFalse();
        assertThat(preflight.job().snapshot().snapshotDigest()).isEqualTo(live.snapshotDigest());
        assertThat(preflight.job().analysisResult().decisions()).hasSize(2);
    }

    @Test
    void buildChangeReanalyzesLatestSnapshotAndRevokesApproval() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob job = service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob reviewed = service.submitSnapshot(
                job.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        ArtifactAnalysisJob approved = service.approve(job.id(), source.snapshotDigest());

        ArtifactAnalysisJob refreshed = service.reanalyzeReviewable(
                        source.uid(),
                        List.of(build().withStates(false, true)),
                        ArtifactAnalysisPolicy.defaults())
                .getFirst();

        assertThat(refreshed.status()).isEqualTo(ArtifactAnalysisJobStatus.READY_FOR_REVIEW);
        assertThat(refreshed.decisionPlan().approved()).isFalse();
        assertThat(refreshed.decisionPlan().planId())
                .isNotEqualTo(approved.decisionPlan().planId());
        assertThat(refreshed.analysisResult().buildIds()).isEmpty();
        assertThat(refreshed.snapshot().snapshotDigest())
                .isEqualTo(reviewed.snapshot().snapshotDigest());
    }

    @Test
    void buildChangeRevokesEveryReviewablePlanForTheUid() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob first = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        ArtifactAnalysisJob second = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(first.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(first.id(), source.snapshotDigest());
        service.submitSnapshot(second.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(second.id(), source.snapshotDigest());

        var refreshed = service.reanalyzeReviewable(
                source.uid(), List.of(build().withStates(false, true)),
                ArtifactAnalysisPolicy.defaults());

        assertThat(refreshed).hasSize(2)
                .allMatch(job -> job.status() == ArtifactAnalysisJobStatus.READY_FOR_REVIEW)
                .allMatch(job -> !job.decisionPlan().approved());
    }

    @Test
    void buildChangeRevokesWaitingExecutionBeforeReanalyzing() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactJobStartResponse execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);

        ArtifactBuild changedBuild = build().withStates(false, true);
        service.mutateAnalysisConfigurationAndReanalyze(
                source.uid(), () -> changedBuild,
                () -> List.of(changedBuild), ArtifactAnalysisPolicy::defaults);

        assertThat(service.get(execution.job().id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.STALE_ABORT);
        assertThat(service.get(analysis.id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.READY_FOR_REVIEW);
        assertThatThrownBy(() -> launchRequestService().consume(
                execution.launch().requestToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void buildChangeIsRejectedWhileHostOwnsAnExecution() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactAnalysisJob execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        service.claim(execution.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        AtomicBoolean mutated = new AtomicBoolean();

        assertThatThrownBy(() -> service.mutateAnalysisConfigurationAndReanalyze(
                source.uid(), () -> mutated.compareAndSet(false, true),
                () -> List.of(build()), ArtifactAnalysisPolicy::defaults))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正在执行");
        assertThat(mutated).isFalse();
        assertThat(service.get(analysis.id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.APPROVED);
    }

    @Test
    void globalBuildChangeIsRejectedWhileAnotherUidExecutes() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactAnalysisJob execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        service.claim(execution.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        AtomicBoolean mutated = new AtomicBoolean();

        assertThatThrownBy(() -> service.mutateAnalysisConfigurationAndReanalyze(
                "123456789", () -> mutated.compareAndSet(false, true),
                () -> List.of(build()), ArtifactAnalysisPolicy::defaults))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("全部账号");
        assertThat(mutated).isFalse();
    }

    @Test
    void globalBuildChangeRevokesAnotherUidWaitingExecution() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactJobStartResponse execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);

        service.mutateAnalysisConfigurationAndReanalyze(
                "123456789", () -> true,
                () -> List.of(build()), ArtifactAnalysisPolicy::defaults);

        assertThat(service.get(execution.job().id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.STALE_ABORT);
        assertThatThrownBy(() -> launchRequestService().consume(
                execution.launch().requestToken()))
                .hasMessageContaining("not found");
    }

    @Test
    void staleJobIsRejectedBeforeConsumingItsLaunchAuthorization() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactAnalysisJob execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        service.mutateAnalysisConfigurationAndReanalyze(
                "123456789", () -> true,
                () -> List.of(build()), ArtifactAnalysisPolicy::defaults);
        AtomicBoolean authorizationConsumed = new AtomicBoolean();

        assertThatThrownBy(() -> service.claimAuthorized(
                execution.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN,
                () -> authorizationConsumed.set(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能被");
        assertThat(authorizationConsumed).isFalse();
    }

    @Test
    void onlyAnApprovedAnalysisCanLaunchItsLockExecution() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob job = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();

        assertThatThrownBy(() -> service.launch(job.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved");

        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(job.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(job.id(), source.snapshotDigest());

        ArtifactJobStartResponse launch = service.launch(
                job.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        assertThat(launch.job().id()).isNotEqualTo(job.id());
        assertThat(launch.job().operation()).isEqualTo(ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        assertThat(launch.job().status()).isEqualTo(ArtifactAnalysisJobStatus.WAITING_FOR_HOST);
        assertThat(service.get(job.id()).status()).isEqualTo(ArtifactAnalysisJobStatus.APPROVED);
        assertThat(launch.launch().launchUri()).startsWith("BetterGIArtifact://execute?request=");
        ArtifactLaunchRequest request = new ArtifactLaunchRequestService(
                tempDirectory, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5))
                .consume(launch.launch().requestToken());
        assertThat(request.sourceArtifactCount()).isEqualTo(1);
        assertThat(request.targets()).hasSize(1);
        assertThat(request.targets().getFirst().scanIndex()).isZero();
        assertThat(request.jobId()).isEqualTo(launch.job().id());
    }

    @Test
    void filteredLaunchExecutesOnlyTheApprovedChangingTargetSubset() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob sourceJob = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(
                item(0, false), item(1, false), item(2, true)));
        service.submitSnapshot(sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");
        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, List.of(0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, List.of(99)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved plan");
        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, List.of(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state change");

        ArtifactJobStartResponse launch = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, List.of(1));

        assertThat(launch.job().decisionPlan().decisions())
                .extracting(com.cloud_guest.artifact.analysis.ArtifactDecision::scanIndex)
                .containsExactly(1);
        ArtifactLaunchRequest request = launchRequestService().consume(launch.launch().requestToken());
        assertThat(request.targets()).extracting(ArtifactLaunchTarget::scanIndex).containsExactly(1);

        service.claim(launch.job().id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        ArtifactJobPreflightResponse preflight = service.preflight(
                launch.job().id(),
                new ArtifactExecutionObservation(source.uid(), source.artifactCount(), List.of(), null, true),
                List.of(build()), ArtifactAnalysisPolicy.defaults());
        assertThat(preflight.preflight().actions())
                .extracting(com.cloud_guest.artifact.execution.ArtifactExecutionAction::scanIndex)
                .containsExactly(1);
    }

    @Test
    void approvedPlanCanBeExecutedAgainAfterCompletionOrFailure() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob sourceJob = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        ArtifactAnalysisJob firstAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        service.claim(firstAttempt.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        ArtifactExecutionObservation observation = new ArtifactExecutionObservation(
                source.uid(), source.artifactCount(), List.of(), null, true);
        ArtifactJobPreflightResponse preflight = service.preflight(
                firstAttempt.id(), observation, List.of(build()), ArtifactAnalysisPolicy.defaults());
        assertThat(preflight.job().status()).isEqualTo(ArtifactAnalysisJobStatus.READY_TO_EXECUTE);

        ArtifactAnalysisJob completed = service.complete(
                firstAttempt.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, true, null);
        assertThat(completed.status()).isEqualTo(ArtifactAnalysisJobStatus.COMPLETED);

        ArtifactAnalysisJob secondAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        assertThat(secondAttempt.id()).isNotEqualTo(firstAttempt.id());
        service.claim(secondAttempt.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        service.complete(secondAttempt.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN,
                false, "用户已停止任务");
        assertThat(service.get(secondAttempt.id()).status()).isEqualTo(ArtifactAnalysisJobStatus.FAILED);

        ArtifactAnalysisJob thirdAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        assertThat(thirdAttempt.id()).isNotIn(firstAttempt.id(), secondAttempt.id());
        assertThat(thirdAttempt.status()).isEqualTo(ArtifactAnalysisJobStatus.WAITING_FOR_HOST);
        assertThat(service.get(sourceJob.id()).status()).isEqualTo(ArtifactAnalysisJobStatus.APPROVED);
    }

    @Test
    void samePlanCannotLaunchTwoConcurrentExecutionAttempts() {
        ArtifactAnalysisJobService service = service();
        ArtifactAnalysisJob sourceJob = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        service.launch(sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);

        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being executed");
    }

    @Test
    void expiredUnclaimedExecutionStopsPollingAndDoesNotBlockRelaunch() {
        InMemoryArtifactAnalysisJobRepository repository =
                new InMemoryArtifactAnalysisJobRepository();
        ArtifactAnalysisJobService service = service(repository);
        ArtifactAnalysisJob sourceJob = service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(
                sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        ArtifactAnalysisJob firstAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        repository.save(new ArtifactAnalysisJob(
                firstAttempt.id(), firstAttempt.uid(), firstAttempt.operation(),
                firstAttempt.status(), firstAttempt.snapshot(), firstAttempt.analysisResult(),
                firstAttempt.decisionPlan(), "2026-08-26T23:54:00Z",
                "2026-08-26T23:54:00Z", firstAttempt.errorMessage()));

        ArtifactAnalysisJob expired = service.get(firstAttempt.id());
        assertThat(expired.status()).isEqualTo(ArtifactAnalysisJobStatus.FAILED);
        assertThat(expired.errorMessage()).contains("连接 BetterGI 的请求已过期");

        ArtifactAnalysisJob secondAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        assertThat(secondAttempt.id()).isNotEqualTo(firstAttempt.id());
    }

    @Test
    void consumedExecutionRequestRemainsActivePastItsLaunchTtl() {
        InMemoryArtifactAnalysisJobRepository repository =
                new InMemoryArtifactAnalysisJobRepository();
        ArtifactAnalysisJobService service = service(repository);
        ArtifactAnalysisJob sourceJob = service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(
                sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        ArtifactJobStartResponse firstAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        launchRequestService().consume(firstAttempt.launch().requestToken());
        ArtifactAnalysisJob waiting = firstAttempt.job();
        repository.save(new ArtifactAnalysisJob(
                waiting.id(), waiting.uid(), waiting.operation(), waiting.status(),
                waiting.snapshot(), waiting.analysisResult(), waiting.decisionPlan(),
                "2026-08-26T23:54:00Z", "2026-08-26T23:54:00Z", waiting.errorMessage()));

        assertThat(service.get(waiting.id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.WAITING_FOR_HOST);
        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being executed");
    }

    @Test
    void completedExecutionRequestRemainsActiveUntilTheJobTerminalStateIsSaved() {
        InMemoryArtifactAnalysisJobRepository repository =
                new InMemoryArtifactAnalysisJobRepository();
        ArtifactAnalysisJobService service = service(repository);
        ArtifactAnalysisJob sourceJob = service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        service.submitSnapshot(
                sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(sourceJob.id(), source.snapshotDigest());

        ArtifactJobStartResponse firstAttempt = service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        ArtifactLaunchRequestService requests = launchRequestService();
        ArtifactLaunchRequest accepted = requests.consume(firstAttempt.launch().requestToken());
        requests.complete(
                firstAttempt.launch().requestToken(), accepted.uid(), accepted.jobId(), accepted.operation());
        ArtifactAnalysisJob waiting = firstAttempt.job();
        repository.save(new ArtifactAnalysisJob(
                waiting.id(), waiting.uid(), waiting.operation(), waiting.status(),
                waiting.snapshot(), waiting.analysisResult(), waiting.decisionPlan(),
                "2026-08-26T23:54:00Z", "2026-08-26T23:54:00Z", waiting.errorMessage()));

        assertThat(service.get(waiting.id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.WAITING_FOR_HOST);
        assertThatThrownBy(() -> service.launch(
                sourceJob.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being executed");
        assertThatThrownBy(() -> service.delete(waiting.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被 BetterGI 接受");
        assertThat(requests.complete(
                firstAttempt.launch().requestToken(),
                accepted.uid(), accepted.jobId(), accepted.operation()))
                .isEqualTo(accepted);
    }

    @Test
    void approvedPlanCannotLaunchAgainstDifferentBuildInputs() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());

        assertThatThrownBy(() -> service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN, null,
                List.of(build().withStates(false, false)),
                ArtifactAnalysisPolicy.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outdated Build");
    }

    @Test
    void waitingJobCanBeDeletedAndItsLaunchRequestIsRevoked() {
        ArtifactAnalysisJobService service = service();
        ArtifactJobStartResponse start = service.start("102550550", ArtifactLaunchOperation.ANALYZE);

        assertThat(service.delete(start.job().id())).isTrue();
        assertThatThrownBy(() -> service.get(start.job().id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> launchRequestService().consume(start.launch().requestToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void failedLaunchRequestCreationRollsBackInvisibleWaitingJob() throws Exception {
        Path invalidRoot = tempDirectory.resolve("request-root-is-a-file");
        Files.writeString(invalidRoot, "not a directory");
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        ArtifactAnalysisJobService service = new ArtifactAnalysisJobService(
                new InMemoryArtifactAnalysisJobRepository(),
                new ArtifactAnalysisEngine(), new ArtifactExecutionGuard(),
                new ArtifactLaunchRequestService(
                        invalidRoot, new ObjectMapper(), clock, Duration.ofMinutes(5)),
                clock);

        assertThatThrownBy(() -> service.start(
                "102550550", ArtifactLaunchOperation.ANALYZE))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.list("102550550")).isEmpty();
    }

    @Test
    void claimedJobReportsActiveHostAndCannotBeDeleted() {
        ArtifactAnalysisJobService service = service();
        ArtifactJobStartResponse start = service.start("102550550", ArtifactLaunchOperation.ANALYZE);

        ArtifactAnalysisJob claimed = service.claim(
                start.job().id(), start.job().uid(), ArtifactLaunchOperation.ANALYZE);

        assertThat(claimed.status()).isEqualTo(ArtifactAnalysisJobStatus.HOST_CLAIMED);
        assertThat(service.claim(
                claimed.id(), claimed.uid(), ArtifactLaunchOperation.ANALYZE))
                .isEqualTo(claimed);
        assertThatThrownBy(() -> service.delete(claimed.id()))
                .hasMessageContaining("正在执行");
        ArtifactAnalysisJob reviewed = service.submitSnapshot(
                claimed.id(), snapshot(List.of(item(0, false))),
                List.of(build()), ArtifactAnalysisPolicy.defaults());
        assertThat(reviewed.status()).isEqualTo(ArtifactAnalysisJobStatus.READY_FOR_REVIEW);
    }

    @Test
    void claimedRecoveryCanResumeReviewAndReadyToExecutePhases() {
        ArtifactAnalysisJobService service = service();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob analysis = service.start(
                source.uid(), ArtifactLaunchOperation.ANALYZE).job();
        service.claim(analysis.id(), source.uid(), ArtifactLaunchOperation.ANALYZE);
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());

        assertThat(service.claim(
                analysis.id(), source.uid(), ArtifactLaunchOperation.ANALYZE).status())
                .isEqualTo(ArtifactAnalysisJobStatus.HOST_CLAIMED);
        service.submitSnapshot(
                analysis.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        service.approve(analysis.id(), source.snapshotDigest());
        ArtifactAnalysisJob execution = service.launch(
                analysis.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).job();
        service.claim(execution.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        service.preflight(
                execution.id(),
                new ArtifactExecutionObservation(
                        source.uid(), source.artifactCount(), source.artifacts(), null),
                List.of(build()), ArtifactAnalysisPolicy.defaults());

        assertThat(service.claim(
                execution.id(), source.uid(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN).status())
                .isEqualTo(ArtifactAnalysisJobStatus.HOST_CLAIMED);
    }

    @Test
    void claimedWaitingJobCannotBeDeleted() {
        ArtifactAnalysisJobService service = service();
        ArtifactJobStartResponse start = service.start("102550550", ArtifactLaunchOperation.ANALYZE);
        launchRequestService().consume(start.launch().requestToken());

        assertThatThrownBy(() -> service.delete(start.job().id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BetterGI");
        assertThat(service.get(start.job().id())).isEqualTo(start.job());
    }

    @Test
    void outdatedScoringPolicyRequiresRescanAndCannotBeApprovedOrExecuted() {
        InMemoryArtifactAnalysisJobRepository repository = new InMemoryArtifactAnalysisJobRepository();
        ArtifactAnalysisJobService service = service(repository);
        ArtifactAnalysisJob sourceJob = service.start("102550550", ArtifactLaunchOperation.ANALYZE).job();
        ArtifactSnapshot source = snapshot(List.of(item(0, false)));
        ArtifactAnalysisJob reviewed = service.submitSnapshot(
                sourceJob.id(), source, List.of(build()), ArtifactAnalysisPolicy.defaults());
        ArtifactAnalysisResult current = reviewed.analysisResult();
        ArtifactAnalysisResult outdated = new ArtifactAnalysisResult(
                current.snapshotDigest(), "outdated-policy", current.buildIds(),
                current.decisions(), current.summary());
        ArtifactAnalysisJob outdatedReview = new ArtifactAnalysisJob(
                reviewed.id(), reviewed.uid(), reviewed.operation(),
                ArtifactAnalysisJobStatus.READY_FOR_REVIEW,
                reviewed.snapshot(), outdated, reviewed.decisionPlan(),
                reviewed.createdAtUtc(), reviewed.updatedAtUtc(), null);
        repository.save(outdatedReview);

        assertThat(service.get(reviewed.id()).status())
                .isEqualTo(ArtifactAnalysisJobStatus.RESCAN_REQUIRED);
        assertThat(service.get(reviewed.id()).errorMessage()).contains("评分算法已更新");
        assertThatThrownBy(() -> service.approve(reviewed.id(), source.snapshotDigest()))
                .hasMessageContaining("scoring policy");

        ArtifactDecisionPlan approvedPlan = new ArtifactDecisionPlan(
                reviewed.decisionPlan().planId(), reviewed.decisionPlan().uid(),
                reviewed.decisionPlan().sourceArtifactCount(),
                reviewed.decisionPlan().sourceSnapshotDigest(), true,
                reviewed.decisionPlan().decisions());
        repository.save(new ArtifactAnalysisJob(
                reviewed.id(), reviewed.uid(), reviewed.operation(),
                ArtifactAnalysisJobStatus.APPROVED,
                reviewed.snapshot(), outdated, approvedPlan,
                reviewed.createdAtUtc(), reviewed.updatedAtUtc(), null));

        assertThat(service.list(reviewed.uid()).getFirst().status())
                .isEqualTo(ArtifactAnalysisJobStatus.RESCAN_REQUIRED);
        assertThatThrownBy(() -> service.launch(
                reviewed.id(), ArtifactLaunchOperation.EXECUTE_LOCK_PLAN))
                .hasMessageContaining("scoring policy");
    }

    private ArtifactAnalysisJobService service() {
        return service(new InMemoryArtifactAnalysisJobRepository());
    }

    private ArtifactAnalysisJobService service(ArtifactAnalysisJobRepository repository) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        return new ArtifactAnalysisJobService(
                repository,
                new ArtifactAnalysisEngine(),
                new ArtifactExecutionGuard(),
                new ArtifactLaunchRequestService(
                        tempDirectory, new ObjectMapper(), clock, Duration.ofMinutes(5)),
                clock);
    }

    private ArtifactLaunchRequestService launchRequestService() {
        return new ArtifactLaunchRequestService(
                tempDirectory, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
    }

    private static ArtifactAnalysisJob job(String id, String createdAt) {
        return new ArtifactAnalysisJob(
                id, "102550550", ArtifactLaunchOperation.ANALYZE,
                ArtifactAnalysisJobStatus.WAITING_FOR_HOST,
                null, null, null, createdAt, createdAt, null);
    }

    private static final class FixedOrderRepository implements ArtifactAnalysisJobRepository {
        private final List<ArtifactAnalysisJob> jobs;

        private FixedOrderRepository(List<ArtifactAnalysisJob> jobs) {
            this.jobs = List.copyOf(jobs);
        }

        @Override
        public ArtifactAnalysisJob save(ArtifactAnalysisJob job) {
            return job;
        }

        @Override
        public Optional<ArtifactAnalysisJob> findById(String id) {
            return jobs.stream().filter(job -> job.id().equals(id)).findFirst();
        }

        @Override
        public List<ArtifactAnalysisJob> findByUid(String uid) {
            return jobs.stream().filter(job -> job.uid().equals(uid)).toList();
        }

        @Override
        public boolean delete(String uid, String id) {
            return false;
        }
    }

    private static ArtifactSnapshot snapshot(List<ArtifactItem> items) {
        return ArtifactSnapshot.create(
                "102550550", "scan-" + items.size(), "OBTAINED_AT_DESC", "genshin-7.0", items);
    }

    private static ArtifactItem item(int index, boolean locked) {
        return new ArtifactItem(
                index, "GoldenTroupe", "circlet", 20, 5, "hp_",
                List.of(
                        new ArtifactSubstat("critRate_", 11.7),
                        new ArtifactSubstat("critDMG_", 23.3),
                        new ArtifactSubstat("def", 23),
                        new ArtifactSubstat("atk", 19)),
                "Furina", locked);
    }

    private static ArtifactBuild build() {
        return new ArtifactBuild(
                "furina", "后台C", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("critRate_", "critDMG_", "hp_")),
                Map.of("critRate_", 1.0, "critDMG_", 1.0),
                true, true, "genshin-artifact-analyzer@766b1a6a");
    }
}
