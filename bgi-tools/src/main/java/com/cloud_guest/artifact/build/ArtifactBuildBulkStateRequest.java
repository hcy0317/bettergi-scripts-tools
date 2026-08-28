package com.cloud_guest.artifact.build;

public record ArtifactBuildBulkStateRequest(
        String scope,
        String field,
        boolean enabled) {

    public ArtifactBuildBulkStateRequest {
        if (!"all".equals(scope) && !"upstream".equals(scope) && !"custom".equals(scope)) {
            throw new IllegalArgumentException("unsupported artifact build bulk scope");
        }
        if (!"analysisEnabled".equals(field) && !"nativeSyncEnabled".equals(field)) {
            throw new IllegalArgumentException("unsupported artifact build bulk field");
        }
    }
}
