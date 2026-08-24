package com.cloud_guest.cultivation.execution.module;

import java.util.List;
import java.util.Map;

public interface CultivationExecutionModule {
    String moduleId();

    String displayName();

    String adapterVersion();

    String description();

    String integrationState();

    List<String> capabilities();

    List<CultivationModuleSettingField> settingsSchema();

    Map<String, Object> defaultSettings(String uid);

    default boolean defaultEnabled() {
        return true;
    }
}
