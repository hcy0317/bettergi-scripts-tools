package com.cloud_guest.cultivation.execution;

public record CultivationCraftingAction(
        String materialName,
        long quantity,
        String materialType
) {
}
