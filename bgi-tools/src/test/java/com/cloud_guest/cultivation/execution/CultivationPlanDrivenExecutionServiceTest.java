package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CultivationPlanDrivenExecutionServiceTest {

    private static final Clock MONDAY = Clock.fixed(
            Instant.parse("2026-08-24T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void explicitlyEmptyResinSelectionKeepsEveryPhysicalSourceDisabled() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projection());
        when(projectionService.resinPriority("102550550")).thenReturn(List.of());
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        when(mapper.insert(any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "executor-empty-resin");

        assertThat(response.plan().getAutoDomain().getPhysical())
                .allSatisfy(physical -> {
                    assertThat(physical.isOpen()).isFalse();
                    assertThat(physical.getCount()).isZero();
                });
    }

    @Test
    void claimsOnlyOneEligibleLedgerActionAsAOneRoundSafetyBatch() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projection());
        when(projectionService.resinPriority("102550550")).thenReturn(List.of("须臾树脂", "原粹树脂"));
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        doAnswer(invocation -> {
            CultivationExecutionActionEntity entity = invocation.getArgument(0);
            assertThat(entity.getStatus()).isEqualTo("LEASED");
            assertThat(entity.getMaterialName()).isEqualTo("「公平」的哲学");
            assertThat(entity.getRemainingBefore()).isEqualTo(5);
            return 1;
        }).when(mapper).insert(any());

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "executor-a");

        assertThat(response.status()).isEqualTo("ACTION");
        assertThat(response.executionMode()).isEqualTo("PLAN_DRIVEN");
        assertThat(response.materialName()).isEqualTo("「公平」的哲学");
        assertThat(response.remaining()).isEqualTo(5);
        assertThat(response.batchLimit()).isEqualTo(1);
        assertThat(response.plan().getRecord()).isFalse();
        assertThat(response.plan().getAutoDomain().getDomainRoundNum()).isEqualTo(1);
        assertThat(response.plan().getAutoDomain().getPhysical())
                .extracting("order", "name", "open", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "须臾树脂", true, 1L),
                        org.assertj.core.groups.Tuple.tuple(1, "原粹树脂", true, 1L),
                        org.assertj.core.groups.Tuple.tuple(2, "浓缩树脂", false, 0L),
                        org.assertj.core.groups.Tuple.tuple(3, "脆弱树脂", false, 0L));
        assertThat(response.reconcileGrid()).isEqualTo("CharacterDevelopmentItems");
        verify(mapper).insert(any(CultivationExecutionActionEntity.class));
    }

    @Test
    void leasesTheNextMaterialCraftBeforeSpendingMoreResin() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionProjection current = projection();
        CultivationExecutionProjection needsCraft = new CultivationExecutionProjection(
                current.uid(), current.revision(), "NEEDS_CRAFT", current.executionMode(),
                List.of(new CultivationCraftingAction("「笃行」的指引", 3, "角色天赋素材")),
                current.resinActions(), current.bossActions(), current.weeklyBossActions(),
                current.gatherAction(), current.monsterAction(), current.pendingMaterials(),
                current.preferences(), current.partyOptions());
        when(projectionService.projection("102550550")).thenReturn(needsCraft);
        when(projectionService.craftingCountry("102550550")).thenReturn("枫丹");
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "CharacterDevelopmentItems", List.of(
                        "「笃行」的教导", "「笃行」的指引", "「笃行」的哲学")));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(
                completedInventoryBatch()));
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        when(mapper.insert(any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "craft-executor");

        assertThat(response.status()).isEqualTo("ACTION");
        assertThat(response.actionType()).isEqualTo("CRAFT");
        assertThat(response.materialName()).isEqualTo("「笃行」的指引");
        assertThat(response.batchLimit()).isEqualTo(3);
        assertThat(response.craftMaterialType()).isEqualTo("角色天赋素材");
        assertThat(response.craftCountry()).isEqualTo("枫丹");
        assertThat(response.plan()).isNull();
        verify(mapper).insert(any(CultivationExecutionActionEntity.class));
    }

    @Test
    void requiresACompleteFamilyInventoryBatchBeforeLeasingCraft() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionProjection current = projection();
        CultivationExecutionProjection needsCraft = new CultivationExecutionProjection(
                current.uid(), current.revision(), "NEEDS_CRAFT", current.executionMode(),
                List.of(new CultivationCraftingAction("「笃行」的指引", 3, "角色天赋素材")),
                current.resinActions(), current.bossActions(), current.weeklyBossActions(),
                current.gatherAction(), current.monsterAction(), current.pendingMaterials(),
                current.preferences(), current.partyOptions());
        when(projectionService.projection("102550550")).thenReturn(needsCraft);
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "CharacterDevelopmentItems", List.of(
                        "「笃行」的教导", "「笃行」的指引", "「笃行」的哲学")));
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of());
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "craft-executor");

        assertThat(response.status()).isEqualTo("PLAN_NEEDS_RECONCILE");
        verify(mapper, never()).insert(any());
    }

    @Test
    void requiresAnotherFamilyInventoryBatchAfterACompletedCraft() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionProjection current = projection();
        CultivationExecutionProjection needsCraft = new CultivationExecutionProjection(
                current.uid(), current.revision(), "NEEDS_CRAFT", current.executionMode(),
                List.of(new CultivationCraftingAction("「笃行」的哲学", 1, "角色天赋素材")),
                current.resinActions(), current.bossActions(), current.weeklyBossActions(),
                current.gatherAction(), current.monsterAction(), current.pendingMaterials(),
                current.preferences(), current.partyOptions());
        when(projectionService.projection("102550550")).thenReturn(needsCraft);
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "CharacterDevelopmentItems", List.of(
                        "「笃行」的教导", "「笃行」的指引", "「笃行」的哲学")));
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        CultivationExecutionActionEntity craft = leasedAction("completed-craft");
        craft.setActionType("CRAFT");
        craft.setStatus("COMPLETED");
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(
                craft, completedInventoryBatch()));
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        assertThat(service.claim("102550550", "craft-executor").status())
                .isEqualTo("PLAN_NEEDS_RECONCILE");
        verify(mapper, never()).insert(any());
    }

    @Test
    void doesNotHandTheSameActiveLeaseToAnotherExecutor() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projection());
        CultivationExecutionActionEntity leased = new CultivationExecutionActionEntity();
        leased.setId("action-1");
        leased.setUid("102550550");
        leased.setPlanRevision(3);
        leased.setExecutorId("executor-a");
        leased.setStatus("LEASED");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).plusMinutes(10));
        when(mapper.findLeased("102550550", 3)).thenReturn(leased);

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "executor-b");

        assertThat(response.status()).isEqualTo("BUSY");
        assertThat(response.actionId()).isEqualTo("action-1");
    }

    @Test
    void completesIdempotentlyOnlyWithAnAuthoritativeInventoryObservation() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = new CultivationExecutionActionEntity();
        leased.setId("action-1");
        leased.setUid("102550550");
        leased.setPlanRevision(3);
        leased.setExecutorId("executor-a");
        leased.setMaterialName("「公平」的哲学");
        leased.setStatus("LEASED");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).plusMinutes(10));
        when(mapper.selectById("action-1")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);
        CultivationActionResultRequest request = new CultivationActionResultRequest(
                "executor-a", 3, "result-1", true, 8L,
                Map.of("「公平」的哲学", 2), "COMPLETED");

        CultivationActionResultResponse response = service.complete("action-1", request);

        assertThat(response.status()).isEqualTo("REPLANNING");
        assertThat(response.observedOwned()).isEqualTo(8);
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
        assertThat(leased.getResultIdempotencyKey()).isEqualTo("result-1");
        verify(mapper).update(any(CultivationExecutionActionEntity.class), any());

        when(mapper.selectById("action-1")).thenReturn(leased);
        CultivationActionResultResponse repeated = service.complete("action-1", request);
        assertThat(repeated.status()).isEqualTo("REPLANNING");
    }

    @Test
    void blocksReplanningWhenInventoryEvidenceIsUnknown() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = new CultivationExecutionActionEntity();
        leased.setId("action-2");
        leased.setUid("102550550");
        leased.setPlanRevision(3);
        leased.setExecutorId("executor-a");
        leased.setMaterialName("谜土的护符");
        leased.setStatus("LEASED");
        leased.setLeaseKey("102550550:3");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).plusMinutes(10));
        when(mapper.selectById("action-2")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);
        CultivationActionResultResponse response = service.complete("action-2",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-2", true, -2L,
                        Map.of("谜土的护符", 2), "COMPLETED"));

        assertThat(response.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(leased.getStatus()).isEqualTo("AWAITING_RECONCILE");
        assertThat(leased.getLeaseKey()).isEqualTo("102550550:3");

        CultivationActionResultResponse reconciled = service.complete("action-2",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-2", false, 10L,
                        Map.of(), "RECONCILE_ONLY"));
        assertThat(reconciled.status()).isEqualTo("REPLANNING");
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
        assertThat(leased.getLeaseKey()).isNull();
        assertThat(leased.getRewardsJson()).contains("谜土的护符").contains("2");
        assertThat(leased.getTerminationReason()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectsSupplementalInventoryAfterTheReconcileLeaseExpires() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = leasedAction("expired-reconcile");
        awaiting.setStatus("AWAITING_RECONCILE");
        awaiting.setResultIdempotencyKey("expired-reconcile:result");
        awaiting.setRewardsJson("{\"谜土的护符\":2}");
        awaiting.setTerminationReason("COMPLETED");
        awaiting.setLeaseExpiresAt(LocalDateTime.now(MONDAY).minusSeconds(1));
        when(mapper.selectById("expired-reconcile")).thenReturn(awaiting);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        assertThatThrownBy(() -> service.complete("expired-reconcile", new CultivationActionResultRequest(
                "executor-a", 3, "expired-reconcile:result", false, 10L, Map.of(), "RECONCILE_ONLY")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("对账租约已过期");
    }

    @Test
    void recoversAnAwaitingActionBeforeApplyingTheProjectionNeedsReconcileGate() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = leasedAction("awaiting-action");
        awaiting.setActionType("BOSS");
        awaiting.setStatus("AWAITING_RECONCILE");
        awaiting.setResultIdempotencyKey("awaiting-action:result");
        awaiting.setPlanJson("{\"runType\":\"Boss\"}");
        CultivationExecutionProjection blockedProjection = projectionWithState("NEEDS_RECONCILE");
        when(projectionService.projection("102550550")).thenReturn(blockedProjection);
        when(mapper.findLeased("102550550", 3)).thenReturn(awaiting);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "reconcile-executor");

        assertThat(response.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(response.actionId()).isEqualTo("awaiting-action");
        assertThat(awaiting.getExecutorId()).isEqualTo("reconcile-executor");
        verify(mapper).update(any(CultivationExecutionActionEntity.class), any());
    }

    @Test
    void rejectsAResultAfterTheActionLeaseHasExpired() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = leasedAction("expired-action");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).minusSeconds(1));
        when(mapper.selectById("expired-action")).thenReturn(leased);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        assertThatThrownBy(() -> service.complete("expired-action", new CultivationActionResultRequest(
                "executor-a", 3, "expired-result", true, 18L, Map.of(), "COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租约已过期");
    }

    @Test
    void rejectsACompetingResultWhenAnotherIdempotencyKeyWinsTheCas() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = leasedAction("racing-action");
        CultivationExecutionActionEntity winner = leasedAction("racing-action");
        winner.setStatus("COMPLETED");
        winner.setResultIdempotencyKey("winner-result");
        winner.setObservedOwned(18L);
        when(mapper.selectById("racing-action")).thenReturn(leased, winner);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(0);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        assertThatThrownBy(() -> service.complete("racing-action", new CultivationActionResultRequest(
                "executor-a", 3, "loser-result", true, 17L, Map.of(), "COMPLETED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("另一个幂等结果");
    }

    @Test
    void stopsRepeatingTheSameActionWhenTheLatestBatchMadeNoProgress() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projection());
        when(mapper.findLeased("102550550", 3)).thenReturn(null);
        CultivationExecutionActionEntity noProgress = new CultivationExecutionActionEntity();
        noProgress.setActionType("DOMAIN");
        noProgress.setMaterialName("「公平」的哲学");
        noProgress.setRemainingBefore(5L);
        noProgress.setRewardsJson("{}");
        noProgress.setObservedOwned(8L);
        noProgress.setUpdateTime(LocalDateTime.now(MONDAY).minusMinutes(1));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(noProgress));

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "executor-a");

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.message()).contains("未产生奖励或库存进展");
    }

    @Test
    void reportsNoProgressAsTerminalForTheCurrentRun() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = new CultivationExecutionActionEntity();
        leased.setId("action-no-progress");
        leased.setUid("102550550");
        leased.setPlanRevision(3);
        leased.setExecutorId("executor-a");
        leased.setMaterialName("谜土的护符");
        leased.setStatus("LEASED");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).plusMinutes(10));
        when(mapper.selectById("action-no-progress")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationActionResultResponse response = service.complete("action-no-progress",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-no-progress", false, 10L,
                        Map.of(), "NO_PROGRESS:NO_REWARDS"));

        assertThat(response.status()).isEqualTo("STOPPED_NO_PROGRESS");
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void exposesGatherAndMonsterMaterialsForTheFinalAuthoritativeInventoryReconcile() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "Materials", List.of("沙脂蛹"),
                "CharacterDevelopmentItems", List.of("织金红绸")));
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse response =
                service.claimInventoryReconcile("102550550", "inventory-executor");

        assertThat(response.status()).isEqualTo("ACTION");
        assertThat(response.actionId()).isNotBlank();
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.materialNames()).containsExactly("沙脂蛹", "织金红绸");
        assertThat(response.materialNamesByGrid())
                .containsEntry("Materials", List.of("沙脂蛹"))
                .containsEntry("CharacterDevelopmentItems", List.of("织金红绸"));
        verify(mapper).insert(any(CultivationExecutionActionEntity.class));
    }

    @Test
    void returnsBusyWhenARegularCultivationActionStillOwnsTheUidLease() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity regularAction = leasedAction("domain-action");
        regularAction.setActionType("DOMAIN");
        regularAction.setPlanJson("{\"runType\":\"秘境\"}");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(mapper.findLeased("102550550", 3)).thenReturn(regularAction);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse response =
                service.claimInventoryReconcile("102550550", "inventory-executor");

        assertThat(response.status()).isEqualTo("BUSY");
        assertThat(response.actionId()).isEqualTo("domain-action");
        assertThat(response.materialNames()).containsExactly("沙脂蛹", "织金红绸");
    }

    @Test
    void doesNotReopenACompletedInventoryBatchWhenAwaitingLeaseTransferLosesTheCas() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = inventoryBatch("inventory-awaiting");
        awaiting.setStatus("AWAITING_RECONCILE");
        awaiting.setResultIdempotencyKey("inventory-awaiting:result");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(mapper.findLeased("102550550", 3)).thenReturn(awaiting);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(0);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse response =
                service.claimInventoryReconcile("102550550", "second-executor");

        assertThat(response.status()).isEqualTo("BUSY");
        assertThat(response.actionId()).isNull();
        verify(mapper).update(any(CultivationExecutionActionEntity.class), any());
    }

    @Test
    void activeInventoryRetryLeaseCannotBeStolenByAnotherExecutor() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = inventoryBatch("inventory-retry");
        awaiting.setStatus("AWAITING_RECONCILE");
        awaiting.setResultIdempotencyKey("inventory-retry:result");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "Materials", List.of("沙脂蛹"),
                "CharacterDevelopmentItems", List.of("织金红绸")));
        when(mapper.findLeased("102550550", 3)).thenReturn(awaiting);
        when(mapper.selectById("inventory-retry")).thenReturn(awaiting);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse first =
                service.claimInventoryReconcile("102550550", "retry-a");
        CultivationInventoryReconcileTargetsResponse competing =
                service.claimInventoryReconcile("102550550", "retry-b");
        CultivationInventoryObservationResponse completed = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-retry", "retry-a", 3, "inventory-retry:result",
                        Map.of("沙脂蛹", 48L, "织金红绸", 73L)));

        assertThat(first.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(competing.status()).isEqualTo("BUSY");
        assertThat(completed.status()).isEqualTo("REPLANNING");
        assertThat(awaiting.getStatus()).isEqualTo("COMPLETED");
        assertThat(awaiting.getLeaseKey()).isNull();
    }

    @Test
    void legacyInventoryRetryKeepsItsFrozenTargetsWhenTheCatalogExpands() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = inventoryBatch("legacy-inventory-retry");
        awaiting.setStatus("AWAITING_RECONCILE");
        awaiting.setResultIdempotencyKey("legacy-inventory-retry:result");
        awaiting.setPlanJson("[\"织金红绸\"]");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(projectionService.inventoryReconcileTargets("102550550")).thenReturn(Map.of(
                "CharacterDevelopmentItems", List.of("破旧的刀镡", "影打刀镡", "名刀镡")));
        when(mapper.findLeased("102550550", 3)).thenReturn(awaiting);
        when(mapper.selectById("legacy-inventory-retry")).thenReturn(awaiting);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse response =
                service.claimInventoryReconcile("102550550", "retry-a");
        CultivationInventoryObservationResponse completed = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "legacy-inventory-retry", "retry-a", 3,
                        "legacy-inventory-retry:result", Map.of("织金红绸", 73L)));

        assertThat(response.materialNames()).containsExactly("织金红绸");
        assertThat(response.materialNamesByGrid())
                .containsEntry("CharacterDevelopmentItems", List.of("织金红绸"));
        assertThat(completed.status()).isEqualTo("REPLANNING");
    }

    @Test
    void persistsFinalGatherAndMonsterInventoryAsOneLeasedIdempotentBatch() throws Exception {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = inventoryBatch("inventory-action");
        when(mapper.selectById("inventory-action")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);
        CultivationInventoryObservationRequest request = new CultivationInventoryObservationRequest(
                "inventory-action", "inventory-executor", 3, "inventory-action:result",
                Map.of("沙脂蛹", 48L, "织金红绸", 73L));

        CultivationInventoryObservationResponse response = service.recordInventoryObservations(
                "102550550", request);
        CultivationInventoryObservationResponse repeated =
                service.recordInventoryObservations("102550550", request);

        assertThat(response.observedCount()).isEqualTo(2);
        assertThat(repeated.observedCount()).isEqualTo(2);
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
        assertThat(leased.getLeaseKey()).isNull();
        assertThat(new ObjectMapper().readTree(leased.getRewardsJson()))
                .isEqualTo(new ObjectMapper().readTree("{\"沙脂蛹\":48,\"织金红绸\":73}"));
        verify(mapper).update(any(CultivationExecutionActionEntity.class), any());
    }

    @Test
    void usesPreviousTrustedValueForUnknownInventoryWithoutDroppingRecognizedValues() throws Exception {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = inventoryBatch("inventory-unknown");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(mapper.selectById("inventory-unknown")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryObservationRequest request = new CultivationInventoryObservationRequest(
                "inventory-unknown", "inventory-executor", 3, "inventory-unknown:result",
                Map.of("沙脂蛹", -1L, "织金红绸", 73L));
        CultivationInventoryObservationResponse response = service.recordInventoryObservations(
                "102550550", request);
        CultivationInventoryObservationResponse repeated = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-unknown", "inventory-executor", 3, "inventory-unknown:result",
                        Map.of("沙脂蛹", -1L, "织金红绸", 73L)));

        assertThat(response.status()).isEqualTo("REPLANNING");
        assertThat(repeated.status()).isEqualTo("REPLANNING");
        assertThat(response.observedCount()).isEqualTo(2);
        assertThat(response.message()).contains("未知项沿用上次可信库存");
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
        assertThat(leased.getLeaseKey()).isNull();
        assertThat(leased.getTerminationReason()).isEqualTo("INVENTORY_RECONCILE_PARTIAL_WITH_PREVIOUS");
        assertThat(new ObjectMapper().readTree(leased.getRewardsJson()))
                .isEqualTo(new ObjectMapper().readTree("{\"沙脂蛹\":4,\"织金红绸\":73}"));
    }

    @Test
    void keepsUnknownInventoryOpenWhenNoPreviousTrustedValueExists() throws Exception {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = inventoryBatch("inventory-without-fallback");
        when(projectionService.projection("102550550")).thenReturn(projection());
        when(mapper.selectById("inventory-without-fallback")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryObservationResponse response = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-without-fallback", "inventory-executor", 3,
                        "inventory-without-fallback:result", Map.of("沙脂蛹", -1L, "织金红绸", 73L)));

        assertThat(response.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(response.observedCount()).isEqualTo(1);
        assertThat(leased.getStatus()).isEqualTo("AWAITING_RECONCILE");
        assertThat(leased.getLeaseKey()).isEqualTo("102550550:3");
        assertThat(leased.getTerminationReason()).isEqualTo("INVENTORY_RECONCILE_UNKNOWN_PRESERVED");
        assertThat(new ObjectMapper().readTree(leased.getRewardsJson()))
                .isEqualTo(new ObjectMapper().readTree("{\"沙脂蛹\":-1,\"织金红绸\":73}"));
    }

    @Test
    void fillsOmittedInventoryTargetFromPreviousTrustedValue() throws Exception {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = inventoryBatch("inventory-partial");
        when(projectionService.projection("102550550")).thenReturn(projectionWithReconcileTargets());
        when(mapper.selectById("inventory-partial")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryObservationResponse response = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-partial", "inventory-executor", 3, "inventory-partial:result",
                        Map.of("沙脂蛹", 48L)));

        assertThat(response.status()).isEqualTo("REPLANNING");
        assertThat(response.observedCount()).isEqualTo(2);
        assertThat(new ObjectMapper().readTree(leased.getRewardsJson()))
                .isEqualTo(new ObjectMapper().readTree("{\"沙脂蛹\":48,\"织金红绸\":59}"));
    }

    @Test
    void convertsAConcurrentClaimInsertConflictIntoBusy() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity winner = leasedAction("winner-action");
        winner.setExecutorId("executor-winner");
        when(projectionService.projection("102550550")).thenReturn(projection());
        when(mapper.findLeased("102550550", 3)).thenReturn(null, winner);
        doThrow(new org.springframework.dao.DuplicateKeyException("lease race"))
                .when(mapper).insert(any(CultivationExecutionActionEntity.class));
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationNextActionResponse response = service.claim("102550550", "executor-loser");

        assertThat(response.status()).isEqualTo("BUSY");
        assertThat(response.actionId()).isEqualTo("winner-action");
    }

    private static CultivationExecutionProjection projection() {
        var closedToday = new CultivationExecutionProjection.ResinAction(
                "「浪迹」的哲学", 2, "秘境", "无光的深都", "天赋", "钟纳久万",
                "可生成下一步行动", 3, "「浪迹」的哲学", List.of(0, 3, 6));
        var openToday = new CultivationExecutionProjection.ResinAction(
                "「公平」的哲学", 5, "秘境", "苍白的遗荣", "天赋", "钟纳久万",
                "可生成下一步行动", 1, "「公平」的哲学", List.of(0, 1, 4));
        var boss = new CultivationExecutionProjection.BossAction(
                "谜土的护符", 37, "灵觉隐修的迷者", "纳塔", "钟纳久万",
                Map.of("bossStrategyName", "根据队伍自动选择",
                        "bossRewardRecognitionEnabled", true), "待执行");
        return new CultivationExecutionProjection(
                "102550550", 3, "ACTIVE", "计划驱动", List.of(closedToday, openToday),
                List.of(boss), List.of(),
                new CultivationExecutionProjection.GatherAction("CD-Aware-AutoGather", "无", Map.of(), List.of()),
                new CultivationExecutionProjection.MonsterAction(
                        "FullyAutoAndSemiAutoTools", "无", Map.of(), List.of(), List.of()),
                List.of(), new CultivationExecutionPreferences(
                "102550550", "钟纳久万", "钟纳久万", "钟纳久万", true), List.of("钟纳久万"));
    }

    private static CultivationExecutionProjection projectionWithState(String state) {
        CultivationExecutionProjection current = projection();
        return new CultivationExecutionProjection(
                current.uid(), current.revision(), state, current.executionMode(), current.resinActions(),
                current.bossActions(), current.weeklyBossActions(), current.gatherAction(),
                current.monsterAction(), current.pendingMaterials(), current.preferences(), current.partyOptions());
    }

    private static CultivationExecutionActionEntity leasedAction(String id) {
        CultivationExecutionActionEntity leased = new CultivationExecutionActionEntity();
        leased.setId(id);
        leased.setUid("102550550");
        leased.setPlanRevision(3);
        leased.setExecutorId("executor-a");
        leased.setMaterialName("蕈王钩喙");
        leased.setStatus("LEASED");
        leased.setLeaseExpiresAt(LocalDateTime.now(MONDAY).plusMinutes(10));
        return leased;
    }

    private static CultivationExecutionActionEntity inventoryBatch(String id) {
        CultivationExecutionActionEntity leased = leasedAction(id);
        leased.setExecutorId("inventory-executor");
        leased.setActionType("INVENTORY_RECONCILE_BATCH");
        leased.setMaterialName("__inventory_reconcile__");
        leased.setLeaseKey("102550550:3");
        leased.setPlanJson("[\"沙脂蛹\",\"织金红绸\"]");
        return leased;
    }

    private static CultivationExecutionActionEntity completedInventoryBatch() {
        CultivationExecutionActionEntity batch = inventoryBatch("completed-inventory");
        batch.setStatus("COMPLETED");
        batch.setLeaseKey(null);
        batch.setPlanJson("[\"「笃行」的教导\",\"「笃行」的指引\",\"「笃行」的哲学\"]");
        batch.setRewardsJson("{\"「笃行」的教导\":18,\"「笃行」的指引\":6,\"「笃行」的哲学\":2}");
        return batch;
    }

    private static CultivationExecutionProjection projectionWithReconcileTargets() {
        var gatherTarget = new CultivationExecutionProjection.GatherTarget(
                "沙脂蛹", 168, 4, 4, 164, "须弥", "selectLocalSpecialty_须弥");
        var gather = new CultivationExecutionProjection.GatherAction(
                "CD-Aware-AutoGather", "待执行", Map.of(), List.of(gatherTarget));
        var monsterTarget = new CultivationExecutionProjection.MonsterTarget(
                "织金红绸", 129, 59, 59, 70, "镀金旅团", List.of("镀金旅团·机弩兵"));
        var monster = new CultivationExecutionProjection.MonsterAction(
                "FullyAutoAndSemiAutoTools", "待执行", Map.of(), List.of(monsterTarget), List.of("镀金旅团"));
        return new CultivationExecutionProjection(
                "102550550", 3, "ACTIVE", "计划驱动", List.of(), List.of(), List.of(), List.of(),
                gather, monster, List.of(), new CultivationExecutionPreferences(
                "102550550", "", "", "", true), List.of(), List.of(), List.of(
                new CultivationExecutionProjection.MaterialProgress(
                        "沙脂蛹", 4, 168, 164, "沙脂蛹", 0, 1, 0, 0),
                new CultivationExecutionProjection.MaterialProgress(
                        "织金红绸", 59, 129, 70, "织金红绸", 0, 1, 0, 0)));
    }
}
