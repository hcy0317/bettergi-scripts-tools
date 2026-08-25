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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        when(mapper.selectById("action-1")).thenReturn(leased);

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
        verify(mapper).updateById(leased);

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
        when(mapper.selectById("action-2")).thenReturn(leased);

        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);
        CultivationActionResultResponse response = service.complete("action-2",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-2", true, -2L,
                        Map.of("谜土的护符", 2), "COMPLETED"));

        assertThat(response.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(leased.getStatus()).isEqualTo("AWAITING_RECONCILE");

        CultivationActionResultResponse reconciled = service.complete("action-2",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-2", false, 10L,
                        Map.of(), "RECONCILE_ONLY"));
        assertThat(reconciled.status()).isEqualTo("REPLANNING");
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
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
        when(mapper.selectById("action-no-progress")).thenReturn(leased);
        CultivationPlanDrivenExecutionService service = new CultivationPlanDrivenExecutionService(
                projectionService, mapper, new ObjectMapper().findAndRegisterModules(), MONDAY);

        CultivationActionResultResponse response = service.complete("action-no-progress",
                new CultivationActionResultRequest(
                        "executor-a", 3, "result-no-progress", false, 10L,
                        Map.of(), "NO_PROGRESS:NO_REWARDS"));

        assertThat(response.status()).isEqualTo("STOPPED_NO_PROGRESS");
        assertThat(leased.getStatus()).isEqualTo("COMPLETED");
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
}
