package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CultivationMaterialCraftingPlanner {
    private final CultivationMaterialCraftingCatalog catalog;

    public CultivationMaterialCraftingPlanner(CultivationMaterialCraftingCatalog catalog) {
        this.catalog = catalog;
    }

    public CultivationMaterialCraftingPlan plan(List<CultivationLedgerEntry> entries) {
        Map<String, CultivationLedgerEntry> entryByName = new LinkedHashMap<>();
        Map<String, Long> remaining = new LinkedHashMap<>();
        for (CultivationLedgerEntry entry : entries) {
            entryByName.put(entry.materialName(), entry);
            remaining.put(entry.materialName(), entry.remaining());
        }

        List<CultivationCraftingAction> actions = new ArrayList<>();
        Set<String> plannedFamilies = new HashSet<>();
        for (CultivationLedgerEntry entry : entries) {
            var familyOptional = catalog.family(entry.materialName());
            if (familyOptional.isEmpty()) continue;
            var family = familyOptional.get();
            if (!plannedFamilies.add(family.familyName())) continue;
            planFamily(family, entryByName, remaining, actions);
        }
        return new CultivationMaterialCraftingPlan(remaining, actions);
    }

    public boolean isCraftable(String materialName) {
        return catalog.family(materialName).isPresent();
    }

    public java.util.Optional<CultivationMaterialCraftingCatalog.CraftFamily> family(String materialName) {
        return catalog.family(materialName);
    }

    private static void planFamily(CultivationMaterialCraftingCatalog.CraftFamily family,
                                   Map<String, CultivationLedgerEntry> entryByName,
                                   Map<String, Long> remaining,
                                   List<CultivationCraftingAction> actions) {
        List<CultivationMaterialCraftingCatalog.CraftTier> tiers = family.tiers();
        long[] required = new long[tiers.size()];
        long[] available = new long[tiers.size()];
        for (int index = 0; index < tiers.size(); index++) {
            CultivationLedgerEntry entry = entryByName.get(tiers.get(index).materialName());
            if (entry == null) continue;
            required[index] = entry.required();
            available[index] = Math.max(entry.currentOwned(), 0);
        }

        for (int index = 0; index < tiers.size() - 1; index++) {
            long surplus = Math.max(available[index] - required[index], 0);
            long neededAtNextTier = demandAtTier(index + 1, required, available);
            long quantity = Math.min(surplus / 3, neededAtNextTier);
            if (quantity <= 0) continue;
            available[index] -= quantity * 3;
            available[index + 1] += quantity;
            var targetTier = tiers.get(index + 1);
            actions.add(new CultivationCraftingAction(
                    targetTier.materialName(), quantity, targetTier.materialType()));
        }

        for (int index = 0; index < tiers.size(); index++) {
            String name = tiers.get(index).materialName();
            if (entryByName.containsKey(name)) {
                remaining.put(name, Math.max(required[index] - available[index], 0));
            }
        }
    }

    private static long demandAtTier(int start, long[] required, long[] available) {
        long demand = 0;
        for (int index = required.length - 1; index >= start; index--) {
            long requiredHere = required[index] + (index == required.length - 1 ? 0 : demand * 3);
            demand = Math.max(requiredHere - available[index], 0);
        }
        return demand;
    }
}
