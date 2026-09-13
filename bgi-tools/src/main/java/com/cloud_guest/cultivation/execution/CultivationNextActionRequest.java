package com.cloud_guest.cultivation.execution;

public record CultivationNextActionRequest(
        CultivationResinSnapshot resinSnapshot,
        boolean prepareOnly
) {
    public CultivationNextActionRequest(CultivationResinSnapshot resinSnapshot) {
        this(resinSnapshot, false);
    }
}
