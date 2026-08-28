package com.cloud_guest.artifact.build;

public record ArtifactBuildAutoActivationResult(
        int characterCount,
        int favoriteCharacterCount,
        int levelEligibleCharacterCount,
        int eligibleCharacterCount,
        int enabledBuildCount,
        int disabledBuildCount,
        ArtifactBuildAutoActivationSettings settings) {
}
