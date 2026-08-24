package com.cloud_guest.cultivation.execution.module;

import java.util.Map;

public record CultivationModuleConfigurationRequest(
        Boolean enabled,
        Map<String, Object> settings
) {
}
