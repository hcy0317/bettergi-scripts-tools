package com.cloud_guest.artifact.build;

public record ArtifactBuildStateUpdateRequest(
        String field,
        boolean enabled) {

    public ArtifactBuildStateUpdateRequest {
        if (!"analysisEnabled".equals(field)
                && !"nativeSyncEnabled".equals(field)
                && !"quickEquipSyncEnabled".equals(field)) {
            throw new IllegalArgumentException("unsupported artifact build state field");
        }
    }
}
