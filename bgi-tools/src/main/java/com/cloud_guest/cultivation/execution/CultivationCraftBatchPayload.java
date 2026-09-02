package com.cloud_guest.cultivation.execution;

import java.util.List;

public record CultivationCraftBatchPayload(
        String country,
        List<CultivationCraftingAction> actions
) {
    public CultivationCraftBatchPayload {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
