package com.cloud_guest.cultivation.plan;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record CultivationPlanRevisionResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        long id,
        String uid,
        int revision,
        String state,
        String catalogVersion,
        @JsonSerialize(using = ToStringSerializer.class)
        long previewId,
        String sourceImageSha256,
        String engineVersion,
        String modelSource,
        List<CultivationLedgerEntry> requirements,
        LocalDateTime createdAt
) {
}
