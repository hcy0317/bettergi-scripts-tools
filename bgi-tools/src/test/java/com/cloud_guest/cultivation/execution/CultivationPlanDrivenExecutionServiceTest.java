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
import static org.mockito.Mockito.when;

class CultivationPlanDrivenExecutionServiceTest {

    private static final Clock MONDAY = Clock.fixed(
            Instant.parse("2026-08-24T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void claimsOnlyOneEligibleLedgerActionAsAOneRoundSafetyBatch() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(projectionService.projection("102550550")).thenReturn(projection());
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
        assertThat(response.reconcileGrid()).isEqualTo("Materials");
        verify(mapper).insert(any(CultivationExecutionActionEntity.class));
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
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryReconcileTargetsResponse response =
                service.claimInventoryReconcile("102550550", "inventory-executor");

        assertThat(response.status()).isEqualTo("ACTION");
        assertThat(response.actionId()).isNotBlank();
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.materialNames()).containsExactly("沙脂蛹", "织金红绸");
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
    void persistsUnknownFinalInventoryAsAReconcileBlocker() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity leased = inventoryBatch("inventory-unknown");
        when(mapper.selectById("inventory-unknown")).thenReturn(leased);
        when(mapper.update(any(CultivationExecutionActionEntity.class), any())).thenReturn(1);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationInventoryObservationResponse response = service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-unknown", "inventory-executor", 3, "inventory-unknown:result",
                        Map.of("沙脂蛹", -1L, "织金红绸", 73L)));

        assertThat(response.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(response.observedCount()).isEqualTo(1);
        assertThat(leased.getStatus()).isEqualTo("AWAITING_RECONCILE");
        assertThat(leased.getLeaseKey()).isEqualTo("102550550:3");
    }

    @Test
    void rejectsPartialFinalInventoryInsteadOfSilentlyDroppingUnknownTargets() {
        CultivationExecutionService projectionService = mock(CultivationExecutionService.class);
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(mapper.selectById("inventory-partial")).thenReturn(inventoryBatch("inventory-partial"));
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        assertThatThrownBy(() -> service.recordInventoryObservations(
                "102550550", new CultivationInventoryObservationRequest(
                        "inventory-partial", "inventory-executor", 3, "inventory-partial:result",
                        Map.of("沙脂蛹", 48L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整覆盖");
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
                "102550550", 3, "ACTIVE", "计划驱动", List.of(), List.of(), List.of(),
                gather, monster, List.of(), new CultivationExecutionPreferences(
                "102550550", "", "", "", true), List.of());
    }
}
