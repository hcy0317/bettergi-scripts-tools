package com.cloud_guest.controller;

import com.cloud_guest.cultivation.execution.CultivationExecutionPreferences;
import com.cloud_guest.cultivation.execution.CultivationExecutionProjection;
import com.cloud_guest.cultivation.execution.CultivationExecutionService;
import com.cloud_guest.cultivation.execution.CultivationScriptGroupSyncService;
import com.cloud_guest.cultivation.execution.CultivationScriptSyncResult;
import com.cloud_guest.cultivation.execution.CultivationOneStopResult;
import com.cloud_guest.cultivation.execution.CultivationLaunchResult;
import com.cloud_guest.cultivation.execution.CultivationOneStopService;
import com.cloud_guest.cultivation.execution.CultivationActionResultRequest;
import com.cloud_guest.cultivation.execution.CultivationActionResultResponse;
import com.cloud_guest.cultivation.execution.CultivationNextActionResponse;
import com.cloud_guest.cultivation.execution.CultivationPlanDrivenExecutionService;
import com.cloud_guest.cultivation.execution.CultivationInventoryObservationRequest;
import com.cloud_guest.cultivation.execution.CultivationInventoryObservationResponse;
import com.cloud_guest.cultivation.execution.CultivationInventoryReconcileTargetsResponse;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfiguration;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationRequest;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.plan.ConfirmCultivationImportRequest;
import com.cloud_guest.cultivation.plan.CultivationImportPreviewResponse;
import com.cloud_guest.cultivation.plan.CultivationPlanApplicationService;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.cloud_guest.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.cloud_guest.result.Result.ok;

import java.util.List;

@Tag(name = "CultivationPlan")
@RestController
@RequestMapping({
        "/auto/plan/cultivation/",
        "/api/auto/plan/cultivation/",
        "/jwt/auto/plan/cultivation/"
})
public class CultivationPlanController {
    private final CultivationPlanApplicationService service;
    private final CultivationExecutionService executionService;
    private final CultivationModuleConfigurationService moduleConfigurationService;
    private final CultivationScriptGroupSyncService scriptGroupSyncService;
    private final CultivationOneStopService oneStopService;
    private final CultivationPlanDrivenExecutionService planDrivenExecutionService;

    public CultivationPlanController(CultivationPlanApplicationService service,
                                     CultivationExecutionService executionService,
                                     CultivationModuleConfigurationService moduleConfigurationService,
                                     CultivationScriptGroupSyncService scriptGroupSyncService,
                                     CultivationOneStopService oneStopService,
                                     CultivationPlanDrivenExecutionService planDrivenExecutionService) {
        this.service = service;
        this.executionService = executionService;
        this.moduleConfigurationService = moduleConfigurationService;
        this.scriptGroupSyncService = scriptGroupSyncService;
        this.oneStopService = oneStopService;
        this.planDrivenExecutionService = planDrivenExecutionService;
    }

    @PostMapping("import/preview")
    @Operation(summary = "识别养成计算器图片并生成可校正预览")
    public Result<CultivationImportPreviewResponse> preview(@RequestParam String uid,
                                                            @RequestPart("file") MultipartFile file) {
        return ok(service.preview(uid, file));
    }

    @PostMapping("import/confirm")
    @Operation(summary = "确认校正后的材料并生成不可变计划 revision")
    public Result<CultivationPlanRevisionResponse> confirm(
            @Valid @RequestBody ConfirmCultivationImportRequest request) {
        CultivationPlanRevisionResponse confirmed = service.confirm(request);
        oneStopService.prepare(confirmed.uid());
        return ok(confirmed);
    }

    @GetMapping("plan/latest")
    @Operation(summary = "读取 UID 最新养成材料账本")
    public Result<CultivationPlanRevisionResponse> latest(@RequestParam String uid) {
        return ok(executionService.latestLedger(uid));
    }

    @GetMapping("execution/projection")
    @Operation(summary = "将最新养成账本投影为自动体力与采集行动")
    public Result<CultivationExecutionProjection> projection(@RequestParam String uid) {
        return ok(executionService.projection(uid));
    }

    @PostMapping("execution/next-action")
    @Operation(summary = "领取一个带租约的养成行动；每次只发放一个安全批次")
    public Result<CultivationNextActionResponse> nextAction(
            @RequestParam String uid,
            @RequestParam String executorId) {
        return ok(planDrivenExecutionService.claim(uid, executorId));
    }

    @PostMapping("execution/actions/{actionId}/result")
    @Operation(summary = "回写行动结果和权威背包持有量，然后重新规划")
    public Result<CultivationActionResultResponse> actionResult(
            @PathVariable String actionId,
            @RequestBody CultivationActionResultRequest request) {
        CultivationActionResultResponse response = planDrivenExecutionService.complete(actionId, request);
        if (response.observedOwned() != null && response.observedOwned() >= 0) {
            oneStopService.prepare(response.uid());
        }
        return ok(response);
    }

    @PostMapping("execution/inventory-reconcile-targets")
    @Operation(summary = "领取组末地方特产与怪物材料权威背包复核租约")
    public Result<CultivationInventoryReconcileTargetsResponse> inventoryReconcileTargets(
            @RequestParam String uid,
            @RequestParam String executorId) {
        return ok(planDrivenExecutionService.claimInventoryReconcile(uid, executorId));
    }

    @PostMapping("execution/inventory-observations")
    @Operation(summary = "回写组末地方特产与怪物材料的权威背包持有量")
    public Result<CultivationInventoryObservationResponse> inventoryObservations(
            @RequestParam String uid,
            @RequestBody CultivationInventoryObservationRequest request) {
        CultivationInventoryObservationResponse response =
                planDrivenExecutionService.recordInventoryObservations(uid, request);
        if (response.observedCount() > 0) oneStopService.prepare(response.uid());
        return ok(response);
    }

    @GetMapping("execution/preferences")
    @Operation(summary = "读取 UID 的养成执行偏好")
    public Result<CultivationExecutionPreferences> preferences(@RequestParam String uid) {
        return ok(executionService.preferences(uid));
    }

    @PostMapping("execution/preferences")
    @Operation(summary = "保存 UID 的养成执行偏好")
    public Result<CultivationExecutionPreferences> savePreferences(
            @RequestBody CultivationExecutionPreferences request) {
        CultivationExecutionPreferences saved = executionService.savePreferences(request);
        oneStopService.prepare(saved.uid());
        return ok(saved);
    }

    @GetMapping("execution/modules")
    @Operation(summary = "读取 UID 的集中式脚本模块设置")
    public Result<List<CultivationModuleConfiguration>> modules(@RequestParam String uid) {
        return ok(oneStopService.effectiveModules(uid));
    }

    @PutMapping("execution/modules/{moduleId}")
    @Operation(summary = "更新一个养成执行模块的设置")
    public Result<CultivationModuleConfiguration> saveModule(
            @RequestParam String uid,
            @PathVariable String moduleId,
            @RequestBody CultivationModuleConfigurationRequest request) {
        return ok(oneStopService.saveModuleAndPrepare(uid, moduleId, request));
    }

    @PostMapping("execution/modules/{moduleId}/sync")
    @Operation(summary = "将养成模块设置显式同步到 BetterGI 脚本组")
    public Result<CultivationScriptSyncResult> syncModule(
            @RequestParam String uid,
            @PathVariable String moduleId) {
        return ok(scriptGroupSyncService.sync(uid, moduleId));
    }

    @PostMapping("execution/one-stop/prepare")
    @Operation(summary = "生成 UID 专属养成 AutoPlan 与 BetterGI 脚本组")
    public Result<CultivationOneStopResult> prepareOneStop(@RequestParam String uid) {
        return ok(oneStopService.prepare(uid));
    }

    @PostMapping("execution/one-stop/sync")
    @Operation(summary = "将当前养成账本与脚本设置同步到 UID 专属 BetterGI 脚本组")
    public Result<CultivationOneStopResult> syncOneStop(@RequestParam String uid) {
        return ok(oneStopService.prepare(uid));
    }

    @PostMapping("execution/one-stop/start")
    @Operation(summary = "同步并通过 BetterGI 启动 UID 专属养成脚本组")
    public Result<CultivationLaunchResult> startOneStop(@RequestParam String uid) {
        return ok(oneStopService.start(uid));
    }
}
