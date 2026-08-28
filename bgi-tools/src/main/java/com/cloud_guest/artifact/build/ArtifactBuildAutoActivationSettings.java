package com.cloud_guest.artifact.build;

public record ArtifactBuildAutoActivationSettings(
        int levelThreshold,
        boolean favoriteOverride) {

    public ArtifactBuildAutoActivationSettings {
        if (levelThreshold < 0 || levelThreshold > 90) {
            throw new IllegalArgumentException("character level threshold must be between 0 and 90");
        }
    }

    public static ArtifactBuildAutoActivationSettings defaults() {
        return new ArtifactBuildAutoActivationSettings(80, true);
    }
}
