package com.cloud_guest.artifact.launch;

public enum ArtifactLaunchOperation {
    ANALYZE("analysis"),
    SCAN_CHARACTER_ROSTER("characters"),
    EXECUTE_LOCK_PLAN("execute"),
    REBUILD_NATIVE_PLANS("native-sync");

    private final String uriHost;

    ArtifactLaunchOperation(String uriHost) {
        this.uriHost = uriHost;
    }

    public String uriHost() {
        return uriHost;
    }
}
