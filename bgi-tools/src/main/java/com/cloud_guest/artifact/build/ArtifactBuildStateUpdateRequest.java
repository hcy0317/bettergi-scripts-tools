package com.cloud_guest.artifact.build;

public record ArtifactBuildStateUpdateRequest(
        String field,
        Boolean enabled,
        Integer presetIndex) {

    public ArtifactBuildStateUpdateRequest {
        if (!"analysisEnabled".equals(field)
                && !"nativeSyncEnabled".equals(field)
                && !"quickEquipPresetIndex".equals(field)) {
            throw new IllegalArgumentException("unsupported artifact build state field");
        }
        if ("quickEquipPresetIndex".equals(field)) {
            if (presetIndex == null || presetIndex < 0 || presetIndex > 2) {
                throw new IllegalArgumentException(
                        "quick-equip preset index must be 0, 1, or 2");
            }
        } else if (enabled == null) {
            throw new IllegalArgumentException("artifact build state requires enabled");
        }
    }

    public ArtifactBuildStateUpdateRequest(String field, boolean enabled) {
        this(field, enabled, null);
    }

    public ArtifactBuildStateUpdateRequest(String field, int presetIndex) {
        this(field, null, presetIndex);
    }
}
