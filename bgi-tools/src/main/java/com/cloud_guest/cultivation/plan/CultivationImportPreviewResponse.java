package com.cloud_guest.cultivation.plan;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record CultivationImportPreviewResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        long previewId,
        String uid,
        String imageSha256,
        String engineVersion,
        String modelSource,
        int imageWidth,
        int imageHeight,
        List<CultivationPreviewRow> requirements,
        List<String> warnings
) {
}
