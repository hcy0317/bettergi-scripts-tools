package com.cloud_guest.controller;

import com.cloud_guest.artifact.analysis.ArtifactAnalysisPolicy;
import com.cloud_guest.artifact.build.ArtifactBuildService;
import com.cloud_guest.artifact.build.ArtifactBuildBulkStateRequest;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettings;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationSettingsService;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationResult;
import com.cloud_guest.artifact.build.ArtifactBuildAutoActivationService;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.job.ArtifactAnalysisJob;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobSummary;
import com.cloud_guest.artifact.job.ArtifactAnalysisJobService;
import com.cloud_guest.artifact.job.ArtifactJobPreflightResponse;
import com.cloud_guest.artifact.job.ArtifactJobStartResponse;
import com.cloud_guest.artifact.execution.ArtifactExecutionObservation;
import com.cloud_guest.artifact.launch.ArtifactLaunchOperation;
import com.cloud_guest.artifact.nativeplan.ArtifactNativePlanCompiler;
import com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncPlan;
import com.cloud_guest.artifact.nativeplan.ArtifactNativeSyncStatus;
import com.cloud_guest.artifact.settings.ArtifactAnalysisSettingsService;
import com.cloud_guest.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.cloud_guest.result.Result.ok;

@Tag(name = "ArtifactAnalysis")
@RestController
@RequestMapping("/jwt/artifacts/")
public class ArtifactAnalysisController {
    private final ArtifactBuildService buildService;
    private final ArtifactAnalysisSettingsService settingsService;
    private final ArtifactBuildAutoActivationSettingsService autoActivationSettingsService;
    private final ArtifactBuildAutoActivationService autoActivationService;
    private final ArtifactAnalysisJobService jobService;
    private final ArtifactNativePlanCompiler nativePlanCompiler;

    public ArtifactAnalysisController(
            ArtifactBuildService buildService,
            ArtifactAnalysisSettingsService settingsService,
            ArtifactBuildAutoActivationSettingsService autoActivationSettingsService,
            ArtifactBuildAutoActivationService autoActivationService,
            ArtifactAnalysisJobService jobService,
            ArtifactNativePlanCompiler nativePlanCompiler) {
        this.buildService = buildService;
        this.settingsService = settingsService;
        this.autoActivationSettingsService = autoActivationSettingsService;
        this.autoActivationService = autoActivationService;
        this.jobService = jobService;
        this.nativePlanCompiler = nativePlanCompiler;
    }

    @GetMapping("builds")
    @Operation(summary = "读取全部圣遗物分析 Build")
    public Result<List<ArtifactBuild>> builds() {
        return ok(buildService.list());
    }

    @PutMapping("builds/{buildId}")
    @Operation(summary = "保存一个圣遗物分析 Build")
    public Result<ArtifactBuild> saveBuild(
            @PathVariable String buildId,
            @RequestBody ArtifactBuild build,
            @RequestParam String uid) {
        ArtifactBuild saved = buildService.save(buildId, build);
        refreshLatestLockPlan(uid);
        return ok(saved);
    }

    @PostMapping("builds/import")
    @Operation(summary = "批量导入 analyzer Build")
    public Result<List<ArtifactBuild>> importBuilds(
            @RequestBody List<ArtifactBuild> builds,
            @RequestParam String uid) {
        List<ArtifactBuild> imported = buildService.importAll(builds);
        refreshLatestLockPlan(uid);
        return ok(imported);
    }

    @PutMapping("builds/bulk-state")
    @Operation(summary = "按来源和状态字段批量更新圣遗物 Build")
    public Result<List<ArtifactBuild>> updateBuildBulkState(
            @RequestBody ArtifactBuildBulkStateRequest request,
            @RequestParam String uid) {
        List<ArtifactBuild> updated = buildService.updateBulkState(request);
        refreshLatestLockPlan(uid);
        return ok(updated);
    }

    @DeleteMapping("builds/{buildId}")
    @Operation(summary = "删除一个自定义 Build")
    public Result<Boolean> deleteBuild(
            @PathVariable String buildId,
            @RequestParam String uid) {
        boolean deleted = buildService.delete(buildId);
        if (deleted) refreshLatestLockPlan(uid);
        return ok(deleted);
    }

    @GetMapping("settings")
    @Operation(summary = "读取圣遗物评分设置")
    public Result<ArtifactAnalysisPolicy> settings() {
        return ok(settingsService.get());
    }

    @PutMapping("settings")
    @Operation(summary = "保存圣遗物评分设置")
    public Result<ArtifactAnalysisPolicy> saveSettings(
            @RequestBody ArtifactAnalysisPolicy policy,
            @RequestParam String uid) {
        ArtifactAnalysisPolicy saved = settingsService.save(policy);
        refreshLatestLockPlan(uid);
        return ok(saved);
    }

    @GetMapping("builds/auto-activation/settings")
    @Operation(summary = "读取按游戏角色自动启停 Build 的设置")
    public Result<ArtifactBuildAutoActivationSettings> autoActivationSettings() {
        return ok(autoActivationSettingsService.get());
    }

    @PutMapping("builds/auto-activation/settings")
    @Operation(summary = "保存按游戏角色自动启停 Build 的设置")
    public Result<ArtifactBuildAutoActivationSettings> saveAutoActivationSettings(
            @RequestBody ArtifactBuildAutoActivationSettings settings) {
        return ok(autoActivationSettingsService.save(settings));
    }

    @GetMapping("builds/auto-activation/result")
    @Operation(summary = "读取最近一次角色自动启停统计")
    public Result<ArtifactBuildAutoActivationResult> autoActivationResult(
            @RequestParam String uid) {
        return ok(autoActivationService.latest(uid));
    }

    @PostMapping("builds/auto-activation/jobs")
    @Operation(summary = "创建游戏角色检测与 Build 自动启停任务")
    public Result<ArtifactJobStartResponse> startCharacterRosterJob(
            @RequestParam String uid,
            @RequestParam(defaultValue = "") String gameNickname,
            @RequestParam(defaultValue = "") String miliastraNickname,
            @RequestParam(defaultValue = "MannequinGirl") String miliastraCharacterKey) {
        return ok(jobService.startCharacterRoster(
                uid, autoActivationSettingsService.get(),
                gameNickname, miliastraNickname, miliastraCharacterKey));
    }

    @PostMapping("jobs")
    @Operation(summary = "从网页创建圣遗物宿主任务")
    public Result<ArtifactJobStartResponse> startJob(
            @RequestParam String uid,
            @RequestParam(defaultValue = "ANALYZE") ArtifactLaunchOperation operation,
            @RequestParam(defaultValue = "100") int capacity,
            @RequestParam(defaultValue = "false") boolean confirmReplaceAll,
            @RequestParam(defaultValue = "") String reviewedPlanDigest) {
        if (operation == ArtifactLaunchOperation.REBUILD_NATIVE_PLANS) {
            if (!confirmReplaceAll) {
                throw new IllegalStateException(
                        "native replacement requires explicit destructive confirmation");
            }
            ArtifactNativeSyncPlan plan = nativePlanCompiler.compileReplaceAll(
                    buildService.list(), capacity);
            if (plan.status() != ArtifactNativeSyncStatus.READY) {
                throw new IllegalStateException("native artifact plan is not ready: " + plan.message());
            }
            if (!plan.planDigest().equals(reviewedPlanDigest)) {
                throw new IllegalStateException(
                        "native artifact plan changed after web review; run preview again");
            }
            return ok(jobService.startNative(uid, plan));
        }
        return ok(jobService.start(uid, operation));
    }

    @GetMapping("jobs")
    @Operation(summary = "读取 UID 的圣遗物分析记录")
    public Result<List<ArtifactAnalysisJobSummary>> jobs(@RequestParam String uid) {
        return ok(jobService.listSummaries(uid));
    }

    @GetMapping("jobs/{jobId}")
    @Operation(summary = "读取一个圣遗物分析任务")
    public Result<ArtifactAnalysisJob> job(@PathVariable String jobId) {
        return ok(jobService.get(jobId));
    }

    @DeleteMapping("jobs/{jobId}")
    @Operation(summary = "撤销启动请求并删除圣遗物分析任务")
    public Result<Boolean> deleteJob(@PathVariable String jobId) {
        return ok(jobService.delete(jobId));
    }

    @PostMapping("jobs/{jobId}/snapshot")
    @Operation(summary = "BetterGI 回写扫描快照并触发评分")
    public Result<ArtifactAnalysisJob> submitSnapshot(
            @PathVariable String jobId,
            @RequestBody ArtifactSnapshot snapshot) {
        return ok(jobService.submitSnapshot(
                jobId, snapshot, buildService.list(), settingsService.get()));
    }

    @PostMapping("jobs/{jobId}/approve")
    @Operation(summary = "批准与精确快照绑定的锁定方案")
    public Result<ArtifactAnalysisJob> approve(
            @PathVariable String jobId,
            @RequestParam String snapshotDigest) {
        return ok(jobService.approve(jobId, snapshotDigest));
    }

    @PostMapping("jobs/{jobId}/preflight")
    @Operation(summary = "执行前核验数量、指纹与当前锁状态")
    public Result<ArtifactJobPreflightResponse> preflight(
            @PathVariable String jobId,
            @RequestBody ArtifactSnapshot liveSnapshot) {
        return ok(jobService.preflight(
                jobId,
                new ArtifactExecutionObservation(
                        liveSnapshot.uid(), liveSnapshot.artifactCount(),
                        liveSnapshot.artifacts(), liveSnapshot),
                buildService.list(), settingsService.get()));
    }

    @PostMapping("jobs/{jobId}/launch")
    @Operation(summary = "从已批准分析启动原生锁定执行")
    public Result<ArtifactJobStartResponse> launchApprovedPlan(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "EXECUTE_LOCK_PLAN") ArtifactLaunchOperation operation,
            @RequestBody(required = false) List<Integer> scanIndices) {
        return ok(jobService.launch(jobId, operation, scanIndices));
    }

    @PostMapping("native-sync/preview")
    @Operation(summary = "预检完整替换式原神 Lock Assistance 方案")
    public Result<ArtifactNativeSyncPlan> previewNativeSync(
            @RequestParam(defaultValue = "100") int capacity) {
        return ok(nativePlanCompiler.compileReplaceAll(buildService.list(), capacity));
    }

    private void refreshLatestLockPlan(String uid) {
        jobService.reanalyzeLatest(
                uid, buildService.list(), settingsService.get());
    }
}
