package com.cloud_guest.controller;

import com.cloud_guest.cultivation.execution.CultivationActionResultRequest;
import com.cloud_guest.cultivation.execution.CultivationActionResultResponse;
import com.cloud_guest.cultivation.execution.CultivationExecutionPreferences;
import com.cloud_guest.cultivation.execution.CultivationInventoryObservationRequest;
import com.cloud_guest.cultivation.execution.CultivationInventoryObservationResponse;
import com.cloud_guest.cultivation.execution.CultivationExecutionService;
import com.cloud_guest.cultivation.execution.CultivationOneStopService;
import com.cloud_guest.cultivation.execution.CultivationPlanDrivenExecutionService;
import com.cloud_guest.cultivation.execution.CultivationScriptGroupSyncService;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationRequest;
import com.cloud_guest.cultivation.plan.CultivationPlanApplicationService;
import com.cloud_guest.cultivation.plan.ConfirmCultivationImportRequest;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CultivationPlanControllerTest {

    @Test
    void automaticallyGeneratesTheUidSpecificGroupAfterConfirmingANewLedgerRevision() {
        CultivationPlanApplicationService planService = mock(CultivationPlanApplicationService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        ConfirmCultivationImportRequest request = new ConfirmCultivationImportRequest(
                42L, "102550550", List.of(
                        new com.cloud_guest.cultivation.plan.CultivationRequirementEdit(0, "沙脂蛹", 168, 164)));
        CultivationPlanRevisionResponse confirmed = new CultivationPlanRevisionResponse(
                7L, "102550550", 4, "IMPORTED", "catalog", 42L, "hash", "engine", "model",
                List.of(), LocalDateTime.now());
        when(planService.confirm(request)).thenReturn(confirmed);
        CultivationPlanController controller = new CultivationPlanController(
                planService, mock(CultivationExecutionService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, mock(CultivationPlanDrivenExecutionService.class));

        var result = controller.confirm(request);

        assertThat(result.getData()).isSameAs(confirmed);
        verify(oneStopService).prepare("102550550");
    }

    @Test
    void automaticallyResynchronizesTheUidSpecificPlanAfterAuthoritativeInventoryWriteback() {
        CultivationPlanDrivenExecutionService planDrivenService = mock(CultivationPlanDrivenExecutionService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationActionResultRequest request = new CultivationActionResultRequest(
                "executor-a", 1, "result-a", true, 18L, Map.of(), "COMPLETED");
        CultivationActionResultResponse response = new CultivationActionResultResponse(
                "REPLANNING", "权威库存已回写", "102550550", "action-a", 1, "蕈王钩喙", 18L);
        when(planDrivenService.complete("action-a", request)).thenReturn(response);

        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), mock(CultivationExecutionService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, planDrivenService);

        var result = controller.actionResult("action-a", request);

        assertThat(result.getData()).isSameAs(response);
        verify(oneStopService).prepare("102550550");
    }

    @Test
    void doesNotRegenerateRoutesWhenTheInventoryObservationIsUnknown() {
        CultivationPlanDrivenExecutionService planDrivenService = mock(CultivationPlanDrivenExecutionService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationActionResultRequest request = new CultivationActionResultRequest(
                "executor-a", 1, "result-a", true, -2L, Map.of(), "COMPLETED");
        CultivationActionResultResponse response = new CultivationActionResultResponse(
                "NEEDS_RECONCILE", "库存识别未知", "102550550", "action-a", 1, "蕈王钩喙", -2L);
        when(planDrivenService.complete("action-a", request)).thenReturn(response);
        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), mock(CultivationExecutionService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, planDrivenService);

        controller.actionResult("action-a", request);

        verifyNoInteractions(oneStopService);
    }

    @Test
    void webModuleChangesAreImmediatelyWrittenToTheUidSpecificBetterGiGroup() {
        CultivationModuleConfigurationService configurationService =
                mock(CultivationModuleConfigurationService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationModuleConfigurationRequest request =
                new CultivationModuleConfigurationRequest(true, Map.of("partyName", "网页队伍"));
        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), mock(CultivationExecutionService.class),
                configurationService, mock(CultivationScriptGroupSyncService.class),
                oneStopService, mock(CultivationPlanDrivenExecutionService.class));

        controller.saveModule("102550550", "cd-aware-auto-gather", request);

        verify(configurationService).save("102550550", "cd-aware-auto-gather", request);
        verify(oneStopService).prepare("102550550");
    }

    @Test
    void webPreferenceChangesAreImmediatelyWrittenToTheUidSpecificBetterGiGroup() {
        CultivationExecutionService executionService = mock(CultivationExecutionService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationExecutionPreferences request = new CultivationExecutionPreferences(
                "102550550", "秘境队伍", "采集队伍", "备用队伍", true);
        when(executionService.savePreferences(request)).thenReturn(request);
        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), executionService,
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, mock(CultivationPlanDrivenExecutionService.class));

        controller.savePreferences(request);

        verify(oneStopService).prepare("102550550");
    }

    @Test
    void finalGatherAndMonsterInventoryWritebackRegeneratesTheUidSpecificPlan() {
        CultivationPlanDrivenExecutionService planDrivenService = mock(CultivationPlanDrivenExecutionService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationInventoryObservationRequest request = new CultivationInventoryObservationRequest(
                "inventory-action", "inventory-executor", 3,
                "inventory-action:result", Map.of("沙脂蛹", 48L));
        when(planDrivenService.recordInventoryObservations("102550550", request)).thenReturn(
                new CultivationInventoryObservationResponse(
                        "REPLANNING", "已回写", "102550550", 3, 1));
        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), mock(CultivationExecutionService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, planDrivenService);

        controller.inventoryObservations("102550550", request);

        verify(oneStopService).prepare("102550550");
    }

    @Test
    void unknownFinalInventoryBlocksWithoutRegeneratingRoutes() {
        CultivationPlanDrivenExecutionService planDrivenService = mock(CultivationPlanDrivenExecutionService.class);
        CultivationOneStopService oneStopService = mock(CultivationOneStopService.class);
        CultivationInventoryObservationRequest request = new CultivationInventoryObservationRequest(
                "inventory-action", "inventory-executor", 3,
                "inventory-action:result", Map.of("沙脂蛹", -1L));
        when(planDrivenService.recordInventoryObservations("102550550", request)).thenReturn(
                new CultivationInventoryObservationResponse(
                        "NEEDS_RECONCILE", "存在未知库存", "102550550", 3, 0));
        CultivationPlanController controller = new CultivationPlanController(
                mock(CultivationPlanApplicationService.class), mock(CultivationExecutionService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationScriptGroupSyncService.class),
                oneStopService, planDrivenService);

        controller.inventoryObservations("102550550", request);

        verifyNoInteractions(oneStopService);
    }
}
