package com.cloud_guest.cultivation.execution;

import java.util.List;
import java.util.Map;

public record CultivationMaterialCraftingPlan(
        Map<String, Long> remainingByMaterial,
        List<CultivationCraftingAction> actions
) {
    public CultivationMaterialCraftingPlan {
        remainingByMaterial = Map.copyOf(remainingByMaterial);
        actions = List.copyOf(actions);
    }

    public boolean needsCraft() {
        return !actions.isEmpty();
    }
}
