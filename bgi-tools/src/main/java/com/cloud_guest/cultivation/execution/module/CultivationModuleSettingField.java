package com.cloud_guest.cultivation.execution.module;

import java.util.List;

public record CultivationModuleSettingField(
        String key,
        String label,
        String control,
        boolean editable,
        String optionsSource,
        List<String> options
) {
    public CultivationModuleSettingField(String key,
                                         String label,
                                         String control,
                                         boolean editable,
                                         String optionsSource) {
        this(key, label, control, editable, optionsSource, List.of());
    }
}
