package com.cloud_guest.cultivation.execution.module;

import java.util.List;

public record CultivationModuleDefinition(
        String moduleId,
        String displayName,
        String adapterVersion,
        String description,
        String integrationState,
        List<String> capabilities,
        List<CultivationModuleSettingField> settingsSchema
) {
    public static CultivationModuleDefinition from(CultivationExecutionModule module) {
        return new CultivationModuleDefinition(
                module.moduleId(), module.displayName(), module.adapterVersion(),
                module.description(), module.integrationState(), module.capabilities(), module.settingsSchema());
    }
}
