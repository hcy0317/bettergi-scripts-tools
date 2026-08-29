package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CultivationLedgerObservationService {
    private final CultivationExecutionActionMapper actionMapper;
    private final ObjectMapper objectMapper;
    private final CultivationMaterialCraftingPlanner craftingPlanner;

    @Autowired
    public CultivationLedgerObservationService(CultivationExecutionActionMapper actionMapper,
                                               ObjectMapper objectMapper,
                                               CultivationMaterialCraftingPlanner craftingPlanner) {
        this.actionMapper = actionMapper;
        this.objectMapper = objectMapper;
        this.craftingPlanner = craftingPlanner;
    }

    CultivationLedgerObservationService(CultivationExecutionActionMapper actionMapper,
                                        ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.objectMapper = objectMapper;
        this.craftingPlanner = null;
    }

    public CultivationPlanRevisionResponse effective(CultivationPlanRevisionResponse imported) {
        if (imported == null) return null;
        CultivationExecutionActionEntity active = actionMapper.findLeased(imported.uid(), imported.revision());
        boolean awaitingReconcile = active != null && ("AWAITING_RECONCILE".equals(active.getStatus())
                || "RECONCILE_RETRY_LEASED".equals(active.getStatus()));
        List<CultivationExecutionActionEntity> observations = actionMapper.findCompletedObservations(
                imported.uid(), imported.revision());
        if (observations == null || observations.isEmpty()) {
            return awaitingReconcile ? withState(imported, "NEEDS_RECONCILE", imported.requirements()) : imported;
        }

        Map<String, Long> latestOwned = new LinkedHashMap<>();
        Map<String, Long> actualRewards = new LinkedHashMap<>();
        for (CultivationExecutionActionEntity observation : observations) {
            if ("INVENTORY_RECONCILE_BATCH".equals(observation.getActionType())) {
                readInventoryBatch(observation.getRewardsJson()).forEach((materialName, observedOwned) -> {
                    if (materialName == null || observedOwned == null || observedOwned < 0) return;
                    latestOwned.putIfAbsent(materialName, observedOwned);
                });
                continue;
            }
            if (observation.getObservedOwned() == null || observation.getObservedOwned() < 0) continue;
            latestOwned.putIfAbsent(observation.getMaterialName(), observation.getObservedOwned());
            mergeRewards(actualRewards, observation.getRewardsJson());
        }
        if (latestOwned.isEmpty()) {
            return awaitingReconcile ? withState(imported, "NEEDS_RECONCILE", imported.requirements()) : imported;
        }

        List<EntryProgress> progress = imported.requirements().stream()
                .map(entry -> withEvidence(entry,
                        latestOwned.get(entry.materialName()),
                        actualRewards))
                .toList();
        List<CultivationLedgerEntry> effectiveRequirements = progress.stream()
                .map(EntryProgress::entry).toList();
        boolean inventoryDecreased = hasUnexplainedInventoryDecrease(imported, observations);
        CultivationMaterialCraftingPlan craftingPlan = craftingPlan(effectiveRequirements);
        effectiveRequirements = effectiveRequirements.stream().map(entry -> new CultivationLedgerEntry(
                entry.sourceIndex(), entry.materialName(), entry.required(), entry.baselineOwned(),
                entry.currentOwned(), craftingPlan.remainingByMaterial().getOrDefault(
                        entry.materialName(), entry.remaining()),
                entry.remainingEvidence(), entry.ocrConfidence(), entry.manuallyCorrected(), entry.sourceBlocks()
        )).toList();
        boolean needsCraft = (craftingPlanner == null && progress.stream().anyMatch(EntryProgress::needsCraft))
                || craftingPlan.needsCraft();
        String state = awaitingReconcile || inventoryDecreased
                ? "NEEDS_RECONCILE"
                : needsCraft
                    ? "NEEDS_CRAFT"
                    : effectiveRequirements.stream().allMatch(item -> item.remaining() <= 0)
                        ? "COMPLETED" : "ACTIVE";
        return withState(imported, state, effectiveRequirements);
    }

    public CultivationMaterialCraftingPlan craftingPlan(List<CultivationLedgerEntry> entries) {
        return craftingPlanner == null
                ? new CultivationMaterialCraftingPlan(
                        entries.stream().collect(java.util.stream.Collectors.toMap(
                                CultivationLedgerEntry::materialName,
                                CultivationLedgerEntry::remaining,
                                (left, right) -> right,
                                LinkedHashMap::new)),
                        List.of())
                : craftingPlanner.plan(entries);
    }

    public boolean isCraftable(String materialName) {
        return craftingPlanner != null && craftingPlanner.isCraftable(materialName);
    }

    public Optional<CultivationMaterialCraftingCatalog.CraftFamily> craftingFamily(String materialName) {
        return craftingPlanner == null ? Optional.empty() : craftingPlanner.family(materialName);
    }

    private boolean hasUnexplainedInventoryDecrease(
            CultivationPlanRevisionResponse imported,
            List<CultivationExecutionActionEntity> observations) {
        Map<String, Long> previousOwned = new LinkedHashMap<>();
        imported.requirements().forEach(entry -> {
            if (entry.currentOwned() >= 0) previousOwned.put(entry.materialName(), entry.currentOwned());
        });
        Map<String, Long> allowedCraftConsumption = new LinkedHashMap<>();
        java.util.Set<String> pendingUnexplainedDecrease = new java.util.LinkedHashSet<>();
        List<CultivationExecutionActionEntity> chronological = new ArrayList<>(observations);
        Collections.reverse(chronological);
        for (CultivationExecutionActionEntity observation : chronological) {
            if ("CRAFT".equals(observation.getActionType())
                    && !recordCraftConsumption(observation, allowedCraftConsumption)) {
                return true;
            }
            Map<String, Long> owned = "INVENTORY_RECONCILE_BATCH".equals(observation.getActionType())
                    ? readInventoryBatch(observation.getRewardsJson())
                    : observation.getMaterialName() != null
                        && observation.getObservedOwned() != null && observation.getObservedOwned() >= 0
                        ? Map.of(observation.getMaterialName(), observation.getObservedOwned())
                        : Map.of();
            for (Map.Entry<String, Long> entry : owned.entrySet()) {
                String materialName = entry.getKey();
                Long currentOwned = entry.getValue();
                if (materialName == null || currentOwned == null || currentOwned < 0) continue;
                Long previous = previousOwned.put(materialName, currentOwned);
                long allowed = allowedCraftConsumption.getOrDefault(materialName, 0L);
                allowedCraftConsumption.remove(materialName);
                if (previous != null && currentOwned < previous
                        && previous - currentOwned > allowed) {
                    pendingUnexplainedDecrease.add(materialName);
                } else if ("INVENTORY_RECONCILE_BATCH".equals(observation.getActionType())) {
                    pendingUnexplainedDecrease.remove(materialName);
                }
            }
        }
        return !pendingUnexplainedDecrease.isEmpty();
    }

    private boolean recordCraftConsumption(
            CultivationExecutionActionEntity craft,
            Map<String, Long> allowedCraftConsumption) {
        if (craftingPlanner == null || craft.getPlanJson() == null || craft.getMaterialName() == null) {
            return false;
        }
        try {
            int plannedQuantity = objectMapper.readTree(craft.getPlanJson()).path("quantity").asInt(-1);
            int actualQuantity = craft.getRewardsJson() == null
                    ? -1
                    : objectMapper.readTree(craft.getRewardsJson())
                        .path(craft.getMaterialName()).asInt(-1);
            int quantity = Math.min(plannedQuantity, actualQuantity);
            Optional<CultivationMaterialCraftingCatalog.CraftFamily> family =
                    craftingPlanner.family(craft.getMaterialName());
            if (quantity <= 0 || family.isEmpty()) return false;
            List<CultivationMaterialCraftingCatalog.CraftTier> tiers = family.get().tiers();
            for (int index = 1; index < tiers.size(); index++) {
                if (!tiers.get(index).materialName().equals(craft.getMaterialName())) continue;
                long consumed = Math.multiplyExact((long) quantity, 3L);
                allowedCraftConsumption.merge(
                        tiers.get(index - 1).materialName(), consumed, Math::addExact);
                return true;
            }
            return false;
        } catch (IOException | ArithmeticException exception) {
            return false;
        }
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
                                       Map<String, Long> actualRewards) {
        long exactRewards = actualRewards.getOrDefault(entry.materialName(), 0L);
        Long confirmedOwned = observedOwned == null && exactRewards > 0
                ? Long.valueOf(Math.max(entry.currentOwned(), 0) + exactRewards)
                : observedOwned;
        long observedGain = confirmedOwned == null
                ? 0 : Math.max(confirmedOwned - entry.baselineOwned(), 0);
        long convertibleRewards = convertibleTalentRewards(entry.materialName(), actualRewards);
        long alreadyCrafted = confirmedOwned == null
                ? 0
                : Math.min(convertibleRewards, Math.max(observedGain - exactRewards, 0));
        long uncraftedEquivalent = Math.max(convertibleRewards - alreadyCrafted, 0);
        long remainingAfterObservation = confirmedOwned == null
                ? Math.max(entry.remaining() - exactRewards, 0)
                : Math.max(entry.required() - confirmedOwned, 0);
        long remaining = Math.max(remainingAfterObservation - uncraftedEquivalent, 0);
        boolean needsCraft = remainingAfterObservation > 0
                && remaining == 0
                && uncraftedEquivalent > 0;
        CultivationLedgerEntry effective = new CultivationLedgerEntry(
                entry.sourceIndex(), entry.materialName(), entry.required(), entry.baselineOwned(), confirmedOwned, remaining,
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
