package com.cloud_guest.controller;

import com.cloud_guest.artifact.build.ArtifactBuildService;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationResult;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationService;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.cloud_guest.artifact.character.ArtifactCharacterRoster;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.execution.ArtifactExecutionObservation;
import com.cloud_guest.artifact.job.ArtifactAnalysisJob;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobService;
import com.cloud_guest.artifact.job.ArtifactHostCompletion;
import com.cloud_guest.artifact.job.ArtifactJobPreflightResponse;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequest;
import com.cloud_guest.artifact.launch.ArtifactLaunchRequestService;
import com.cloud_guest.artifact.nativeplan.ArtifactNativePlanCompiler;
import com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncPlan;
import com.cloud_guest.artifact.settings.ArtifactAnalysisSettingsService;
import com.cloud_guest.result.Result;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.cloud_guest.result.Result.ok;

@Hidden
@RestController
@RequestMapping("/artifacts/host/")
public class ArtifactAnalysisHostController {
    private final ArtifactLaunchRequestService launchRequestService;
    private final ArtifactAnalysisJobService jobService;
    private final ArtifactBuildService buildService;
    private final ArtifactBuildAutoActivationService autoActivationService;
    private final ArtifactAnalysisSettingsService settingsService;
    private final ArtifactNativePlanCompiler nativePlanCompiler;

    public ArtifactAnalysisHostController(
            ArtifactLaunchRequestService launchRequestService,
            ArtifactAnalysisJobService jobService,
            ArtifactBuildService buildService,
            ArtifactBuildAutoActivationService autoActivationService,
            ArtifactAnalysisSettingsService settingsService,
            ArtifactNativePlanCompiler nativePlanCompiler) {
        this.launchRequestService = launchRequestService;
        this.jobService = jobService;
        this.buildService = buildService;
        this.autoActivationService = autoActivationService;
        this.settingsService = settingsService;
        this.nativePlanCompiler = nativePlanCompiler;
    }

    @PostMapping("jobs/{jobId}/claim")
    public Result<Boolean> claim(
            @PathVariable String jobId,
            @RequestParam String uid,
            @RequestParam ArtifactLaunchOperation operation,
            @RequestParam String requestToken) {
        launchRequestService.consume(requestToken, uid, jobId, operation);
        jobService.claim(jobId, uid, operation);
        return ok(true);
    }

    @PostMapping("jobs/{jobId}/snapshot")
    public Result<ArtifactAnalysisJob> submitSnapshot(
            @PathVariable String jobId,
            @RequestParam String requestToken,
            @RequestBody ArtifactSnapshot snapshot) {
        authorize(requestToken, snapshot.uid(), jobId, ArtifactLaunchOperation.ANALYZE);
        return ok(jobService.submitSnapshot(
                jobId, snapshot,
                autoActivationService.resolve(snapshot.uid(), buildService.list()),
                settingsService.get()));
    }

    @PostMapping("jobs/{jobId}/preflight")
    public Result<ArtifactJobPreflightResponse> preflight(
            @PathVariable String jobId,
            @RequestParam String requestToken,
            @RequestBody ArtifactExecutionObservation observation) {
        authorize(requestToken, observation.uid(), jobId, ArtifactLaunchOperation.EXECUTE_LOCK_PLAN);
        return ok(jobService.preflight(
                jobId, observation, buildService.list(), settingsService.get()));
    }

    @PostMapping("jobs/{jobId}/characters")
    public Result<ArtifactBuildAutoActivationResult> submitCharacterRoster(
            @PathVariable String jobId,
            @RequestParam String requestToken,
            @RequestBody ArtifactCharacterRoster roster) {
        ArtifactLaunchRequest request = authorize(
                requestToken, roster.uid(), jobId, ArtifactLaunchOperation.SCAN_CHARACTER_ROSTER);
        if (request.characterLevelThreshold() == null || request.favoriteOverride() == null) {
            throw new IllegalStateException("character roster request is missing its activation settings");
        }
        ArtifactBuildAutoActivationResult result =
                jobService.mutateAnalysisConfigurationAndReanalyze(
                        roster.uid(),
                        () -> autoActivationService.apply(
                                roster,
                                new ArtifactBuildAutoActivationSettings(
                                        request.characterLevelThreshold(), request.favoriteOverride())),
                        () -> autoActivationService.resolve(
                                roster.uid(), buildService.list()),
                        settingsService::get);
        return ok(result);
    }

    @PostMapping("native-sync/plan")
    public Result<ArtifactNativeSyncPlan> nativeSyncPlan(
            @RequestParam String jobId,
            @RequestParam String requestToken) {
        String uid = jobService.get(jobId).uid();
        ArtifactLaunchRequest request = authorize(
                requestToken, uid, jobId, ArtifactLaunchOperation.REBUILD_NATIVE_PLANS);
        if (request.nativeCapacity() == null || request.nativePlanDigest() == null) {
            throw new IllegalStateException("native launch request is missing its reviewed plan binding");
        }
        ArtifactNativeSyncPlan plan = nativePlanCompiler.compileReplaceAll(
                autoActivationService.resolve(uid, buildService.list()),
                request.nativeCapacity());
        if (!request.nativePlanDigest().equals(plan.planDigest())) {
            throw new IllegalStateException("native artifact plan changed after web review");
        }
        return ok(plan);
    }

    @PostMapping("jobs/{jobId}/completion")
    public Result<ArtifactAnalysisJob> complete(
            @PathVariable String jobId,
            @RequestParam String requestToken,
            @RequestBody ArtifactHostCompletion completion) {
        ArtifactAnalysisJob job = jobService.get(jobId);
        launchRequestService.complete(
                requestToken, job.uid(), jobId, completion.operation());
        return ok(jobService.complete(
                jobId, completion.operation(), completion.success(), completion.message()));
    }

    private ArtifactLaunchRequest authorize(
            String requestToken,
            String uid,
            String jobId,
            ArtifactLaunchOperation expectedOperation) {
        return launchRequestService.authorizeClaimed(
                requestToken, uid, jobId, expectedOperation);
    }
}
