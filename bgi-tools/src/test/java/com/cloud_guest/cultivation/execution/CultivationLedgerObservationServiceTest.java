package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationLedgerObservationServiceTest {

    @Test
    void defaultsCurrentOwnedToTheImportBaselineWhenReadingAnOlderLedgerRevision() throws Exception {
        CultivationLedgerEntry entry = objectMapper().readValue("""
                {
                  "sourceIndex": 0,
                  "materialName": "蕈王钩喙",
                  "required": 40,
                  "baselineOwned": 6,
                  "remaining": 34,
                  "remainingEvidence": "OCR",
                  "ocrConfidence": 0.99,
                  "manuallyCorrected": false,
                  "sourceBlocks": []
                }
                """, CultivationLedgerEntry.class);

        assertThat(entry.currentOwned()).isEqualTo(6);
    }

    @Test
    void derivesRemainingFromLatestAuthoritativeOwnedCountWithoutMutatingImportRevision() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity newest = observation("action-2", 8);
        CultivationExecutionActionEntity older = observation("action-1", 6);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(newest, older));
        CultivationPlanRevisionResponse imported = revision(10, 4, 6);

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(imported);

        assertThat(effective.state()).isEqualTo("ACTIVE");
        assertThat(effective.requirements()).singleElement().satisfies(entry -> {
            assertThat(entry.baselineOwned()).isEqualTo(4);
            assertThat(entry.currentOwned()).isEqualTo(8);
            assertThat(entry.remaining()).isEqualTo(2);
        });
        assertThat(imported.requirements().getFirst().remaining()).isEqualTo(6);
    }

    @Test
    void marksPlanCompletedOnlyAfterEveryRequirementHasAuthoritativeZeroRemaining() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(observation("action-3", 10)));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("COMPLETED");
        assertThat(effective.requirements().getFirst().remaining()).isZero();
    }

    @Test
    void requiresReconcileInsteadOfSchedulingMoreWhenInventoryDropsBelowImportBaseline() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(observation("action-4", 3)));
        CultivationPlanRevisionResponse imported = revision(10, 4, 6);

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(imported);

        assertThat(effective.state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(effective.requirements().getFirst().remaining()).isEqualTo(6);
        assertThat(effective.requirements().getFirst().currentOwned()).isEqualTo(3);
    }

    @Test
    void requiresReconcileForAnyNegativeInventoryDeltaAfterThePlanStarted() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(
                observation("newest", 6), observation("older", 8)));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper())
                        .effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(effective.requirements().getFirst().currentOwned()).isEqualTo(6);
        assertThat(effective.requirements().getFirst().remaining()).isEqualTo(2);
    }

    @Test
    void keepsUnobservedMaterialsAtBaselineWhenAnotherMaterialTriggersReconcile() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity newest = observation("newest", 6);
        CultivationExecutionActionEntity older = observation("older", 8);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(newest, older));
        CultivationPlanRevisionResponse imported = new CultivationPlanRevisionResponse(
                1L, "102550550", 3, "IMPORTED", "name-only-v1", 2L,
                "sha", "engine", "model",
                List.of(
                        new CultivationLedgerEntry(
                                0, "「公平」的哲学", 10, 4, 6,
                                RemainingEvidence.OCR, 1.0, false, List.of()),
                        new CultivationLedgerEntry(
                                1, "沙脂蛹", 168, 4, 164,
                                RemainingEvidence.OCR, 1.0, false, List.of())),
                LocalDateTime.of(2026, 8, 23, 20, 0));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(imported);

        assertThat(effective.state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(effective.requirements().get(1).currentOwned()).isEqualTo(4);
        assertThat(effective.requirements().get(1).remaining()).isEqualTo(164);
    }

    @Test
    void countsOnlyWholeThreeToOneTalentConversionsFromActualRewards() throws Exception {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity action = observation("action-5", 4);
        action.setRewardsJson(objectMapper().writeValueAsString(java.util.Map.of(
                "「公平」的教导", 9,
                "「公平」的指引", 2)));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(action));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper())
                        .effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("ACTIVE");
        assertThat(effective.requirements().getFirst().remaining()).isEqualTo(5);
    }

    @Test
    void stopsForCraftingInsteadOfClaimingCompletionFromConvertibleRewards() throws Exception {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity action = observation("action-6", 4);
        action.setRewardsJson(objectMapper().writeValueAsString(java.util.Map.of(
                "「公平」的教导", 54)));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(action));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper())
                        .effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("NEEDS_CRAFT");
        assertThat(effective.requirements().getFirst().remaining()).isZero();
    }

    @Test
    void doesNotDoubleCountLowerTierRewardsAlreadyCraftedIntoObservedTopTier() throws Exception {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity action = observation("action-7", 6);
        action.setRewardsJson(objectMapper().writeValueAsString(java.util.Map.of(
                "「公平」的教导", 18)));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(action));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper())
                        .effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("ACTIVE");
        assertThat(effective.requirements().getFirst().remaining()).isEqualTo(4);
    }

    @Test
    void derivesCurrentOwnedFromACompletedInventoryReconcileBatch() throws Exception {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity batch = new CultivationExecutionActionEntity();
        batch.setId("inventory-batch");
        batch.setUid("102550550");
        batch.setPlanRevision(3);
        batch.setStatus("COMPLETED");
        batch.setActionType("INVENTORY_RECONCILE_BATCH");
        batch.setRewardsJson(objectMapper().writeValueAsString(java.util.Map.of("「公平」的哲学", 8L)));
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of(batch));

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(revision(10, 4, 6));

        assertThat(effective.requirements().getFirst().currentOwned()).isEqualTo(8);
        assertThat(effective.requirements().getFirst().remaining()).isEqualTo(2);
    }

    @Test
    void exposesAwaitingInventoryEvidenceAsNeedsReconcileInTheLedger() {
        CultivationExecutionActionMapper mapper = mock(CultivationExecutionActionMapper.class);
        CultivationExecutionActionEntity awaiting = new CultivationExecutionActionEntity();
        awaiting.setStatus("AWAITING_RECONCILE");
        when(mapper.findLeased("102550550", 3)).thenReturn(awaiting);
        when(mapper.findCompletedObservations("102550550", 3)).thenReturn(List.of());

        CultivationPlanRevisionResponse effective =
                new CultivationLedgerObservationService(mapper, objectMapper()).effective(revision(10, 4, 6));

        assertThat(effective.state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(effective.requirements().getFirst().currentOwned()).isEqualTo(4);
    }

    private static CultivationExecutionActionEntity observation(String id, long owned) {
        CultivationExecutionActionEntity entity = new CultivationExecutionActionEntity();
        entity.setId(id);
        entity.setUid("102550550");
        entity.setPlanRevision(3);
        entity.setMaterialName("「公平」的哲学");
        entity.setStatus("COMPLETED");
        entity.setObservedOwned(owned);
        return entity;
    }

    private static CultivationPlanRevisionResponse revision(long required, long owned, long remaining) {
        return new CultivationPlanRevisionResponse(
                1L, "102550550", 3, "IMPORTED", "name-only-v1", 2L,
                "sha", "engine", "model",
                List.of(new CultivationLedgerEntry(
                        0, "「公平」的哲学", required, owned, remaining,
                        RemainingEvidence.OCR, 1.0, false, List.of())),
                LocalDateTime.of(2026, 8, 23, 20, 0));
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
