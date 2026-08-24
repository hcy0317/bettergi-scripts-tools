package com.cloud_guest.cultivation.execution.module;

import java.util.Map;

public record CultivationModuleConfiguration(
        CultivationModuleDefinition module,
        boolean enabled,
        Map<String, Object> settings
) {
}
