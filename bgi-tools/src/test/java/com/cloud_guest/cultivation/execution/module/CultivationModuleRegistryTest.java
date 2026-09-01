package com.cloud_guest.cultivation.execution.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationModuleRegistryTest {
    @Test
    void exposesReplaceableModulesInRegistrationOrder() {
        CultivationModuleRegistry registry = new CultivationModuleRegistry(List.of(
                new AutoPlanResinExecutionModule(),
                new ScriptGroupSettingsExecutionModule(),
                new CdAwareAutoGatherExecutionModule(),
                new FullyAutoToolsExecutionModule(),
                new WeeklyBossExecutionModule()));

        assertThat(registry.all())
                .extracting(CultivationExecutionModule::moduleId)
                .containsExactly(
                        AutoPlanResinExecutionModule.ID,
                        ScriptGroupSettingsExecutionModule.ID,
                        CdAwareAutoGatherExecutionModule.ID,
                        FullyAutoToolsExecutionModule.ID,
                        WeeklyBossExecutionModule.ID);
        assertThat(registry.require(CdAwareAutoGatherExecutionModule.ID).settingsSchema())
                .extracting(CultivationModuleSettingField::key)
                .contains("partyName", "partyName2nd", "targetCountOfSelected", "manualSetAccountName");
        assertThat(registry.require(AutoPlanResinExecutionModule.ID).settingsSchema())
                .extracting(CultivationModuleSettingField::key)
                .doesNotContain(
                        "talentDomainEnabled",
                        "weaponDomainEnabled",
                        "moraLeyLineEnabled",
                        "experienceLeyLineEnabled");
    }
}
