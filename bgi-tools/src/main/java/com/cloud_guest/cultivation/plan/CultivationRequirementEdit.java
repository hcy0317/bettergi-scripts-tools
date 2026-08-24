package com.cloud_guest.cultivation.plan;

public record CultivationRequirementEdit(
        Integer sourceIndex,
        String materialName,
        long required,
        long remaining
) {
}
