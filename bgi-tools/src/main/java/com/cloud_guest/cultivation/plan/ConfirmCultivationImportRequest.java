package com.cloud_guest.cultivation.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConfirmCultivationImportRequest(
        @NotNull Long previewId,
        @NotBlank String uid,
        @NotEmpty List<@Valid CultivationRequirementEdit> requirements
) {
}
