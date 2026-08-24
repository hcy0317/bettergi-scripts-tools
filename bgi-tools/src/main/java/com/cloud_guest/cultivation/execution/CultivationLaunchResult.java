package com.cloud_guest.cultivation.execution;

public record CultivationLaunchResult(
        CultivationOneStopResult preparation,
        String launchUri,
        String message
) {
}
