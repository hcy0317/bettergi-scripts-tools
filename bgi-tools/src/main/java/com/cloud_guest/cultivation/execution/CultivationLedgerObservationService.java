package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CultivationLedgerObservationService {
    private final CultivationExecutionActionMapper actionMapper;
    private final ObjectMapper objectMapper;

    public CultivationLedgerObservationService(CultivationExecutionActionMapper actionMapper,
                                               ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.objectMapper = objectMapper;
    }

    public CultivationPlanRevisionResponse effective(CultivationPlanRevisionResponse imported) {
        if (imported == null) return null;
        CultivationExecutionActionEntity active = actionMapper.findLeased(imported.uid(), imported.revision());
        boolean awaitingReconcile = active != null && "AWAITING_RECONCILE".equals(active.getStatus());
        List<CultivationExecutionActionEntity> observations = actionMapper.findCompletedObservations(
                imported.uid(), imported.revision());
        if (observations == null || observations.isEmpty()) {
            return awaitingReconcile ? withState(imported, "NEEDS_RECONCILE", imported.requirements()) : imported;
        }

        Map<String, Long> latestOwned = new LinkedHashMap<>();
        Map<String, Long> historicalMaxOwned = new LinkedHashMap<>();
        Map<String, Long> actualRewards = new LinkedHashMap<>();
        for (CultivationExecutionActionEntity observation : observations) {
            if ("INVENTORY_RECONCILE_BATCH".equals(observation.getActionType())) {
                readInventoryBatch(observation.getRewardsJson()).forEach((materialName, observedOwned) -> {
                    if (materialName == null || observedOwned == null || observedOwned < 0) return;
                    latestOwned.putIfAbsent(materialName, observedOwned);
                    historicalMaxOwned.merge(materialName, observedOwned, Math::max);
                });
                continue;
            }
            if (observation.getObservedOwned() == null || observation.getObservedOwned() < 0) continue;
            latestOwned.putIfAbsent(observation.getMaterialName(), observation.getObservedOwned());
            historicalMaxOwned.merge(
                    observation.getMaterialName(), observation.getObservedOwned(), Math::max);
            mergeRewards(actualRewards, observation.getRewardsJson());
        }
        if (latestOwned.isEmpty()) {
            return awaitingReconcile ? withState(imported, "NEEDS_RECONCILE", imported.requirements()) : imported;
        }

        boolean unexplainedDecrease = imported.requirements().stream().anyMatch(entry -> {
            Long observedOwned = latestOwned.get(entry.materialName());
            Long historicalMaximum = historicalMaxOwned.get(entry.materialName());
            return observedOwned != null
                    && (observedOwned < entry.baselineOwned()
                        || historicalMaximum != null && observedOwned < historicalMaximum);
        });
        List<EntryProgress> progress = imported.requirements().stream()
                .map(entry -> withEvidence(entry,
                        latestOwned.get(entry.materialName()),
                        historicalMaxOwned.get(entry.materialName()),
                        actualRewards,
                        unexplainedDecrease))
                .toList();
        String state = awaitingReconcile || unexplainedDecrease
                ? "NEEDS_RECONCILE"
                : progress.stream().anyMatch(EntryProgress::needsCraft)
                    ? "NEEDS_CRAFT"
                    : progress.stream().allMatch(item -> item.entry().remaining() <= 0)
                        ? "COMPLETED" : "ACTIVE";
        List<CultivationLedgerEntry> effectiveRequirements = progress.stream()
                .map(EntryProgress::entry).toList();
        return withState(imported, state, effectiveRequirements);
    }

    private static CultivationPlanRevisionResponse withState(
            CultivationPlanRevisionResponse imported,
            String state,
            List<CultivationLedgerEntry> requirements) {
        return new CultivationPlanRevisionResponse(
                imported.id(), imported.uid(), imported.revision(), state, imported.catalogVersion(),
                imported.previewId(), imported.sourceImageSha256(), imported.engineVersion(),
                imported.modelSource(), requirements, imported.createdAt());
    }

    private EntryProgress withEvidence(CultivationLedgerEntry entry,
                                       Long observedOwned,
                                       Long historicalMaximum,
                                       Map<String, Long> actualRewards,
                                       boolean unexplainedDecrease) {
        Long confirmedOwned = unexplainedDecrease && historicalMaximum != null
                ? Math.max(historicalMaximum, entry.baselineOwned())
                : observedOwned;
        long observedGain = confirmedOwned == null
                ? 0 : Math.max(confirmedOwned - entry.baselineOwned(), 0);
        long exactRewards = actualRewards.getOrDefault(entry.materialName(), 0L);
        long convertibleRewards = convertibleTalentRewards(entry.materialName(), actualRewards);
        long alreadyCrafted = confirmedOwned == null
                ? 0
                : Math.min(convertibleRewards, Math.max(observedGain - exactRewards, 0));
        long uncraftedEquivalent = Math.max(convertibleRewards - alreadyCrafted, 0);
        long eventGain = confirmedOwned == null ? exactRewards : 0;
        long remainingAfterObservation = Math.max(entry.remaining() - observedGain - eventGain, 0);
        long remaining = Math.max(remainingAfterObservation - uncraftedEquivalent, 0);
        boolean needsCraft = remainingAfterObservation > 0
                && remaining == 0
                && uncraftedEquivalent > 0;
        CultivationLedgerEntry effective = new CultivationLedgerEntry(
                entry.sourceIndex(), entry.materialName(), entry.required(), entry.baselineOwned(), observedOwned, remaining,
                entry.remainingEvidence(), entry.ocrConfidence(), entry.manuallyCorrected(), entry.sourceBlocks());
        return new EntryProgress(effective, needsCraft);
    }

    private void mergeRewards(Map<String, Long> target, String rewardsJson) {
        if (rewardsJson == null || rewardsJson.isBlank()) return;
        try {
            Map<String, Integer> rewards = objectMapper.readValue(rewardsJson, new TypeReference<>() {});
            rewards.forEach((name, count) -> {
                if (name != null && count != null && count > 0) target.merge(name, count.longValue(), Long::sum);
            });
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取养成行动的实际奖励证据", exception);
        }
    }

    private Map<String, Long> readInventoryBatch(String observationsJson) {
        if (observationsJson == null || observationsJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(observationsJson, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取组末权威库存观察", exception);
        }
    }

    private static long convertibleTalentRewards(String targetMaterial, Map<String, Long> rewards) {
        if (targetMaterial == null || !targetMaterial.endsWith("的哲学")) return 0;
        String family = targetMaterial.substring(0, targetMaterial.length() - "哲学".length());
        long guides = rewards.getOrDefault(family + "指引", 0L);
        long teachings = rewards.getOrDefault(family + "教导", 0L);
        return (guides * 3 + teachings) / 9;
    }

    private record EntryProgress(CultivationLedgerEntry entry, boolean needsCraft) {
    }
}
