package com.cloud_guest.artifact.launch;

public record ArtifactLaunchResult(
        String requestToken,
        String launchUri,
        String expiresAtUtc,
        String message) {
}
