package com.cloud_guest.cultivation.execution.module;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloud_guest.cultivation.execution.BetterGiInstalledScriptSettingsReader;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigEntity;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationModuleConfigurationServiceTest {
    @Test
    void autoPlanExposesAnOrderedSelectionOfResinSources() {
        AutoPlanResinExecutionModule module = new AutoPlanResinExecutionModule();

        assertThat(module.settingsSchema()).anySatisfy(field -> {
            assertThat(field.key()).isEqualTo("resinPriority");
            assertThat(field.control()).isEqualTo("ordered-multi-select");
            assertThat(field.options()).containsExactly(
                    "浓缩树脂", "原粹树脂", "须臾树脂", "脆弱树脂");
        });
        assertThat(module.defaultSettings("102550550").get("resinPriority"))
                .isEqualTo(List.of("浓缩树脂", "原粹树脂"));
        assertThat(module.settingsSchema())
                .extracting(CultivationModuleSettingField::key)
                .contains(
                        "talentDomainEnabled",
                        "weaponDomainEnabled",
                        "moraLeyLineEnabled",
                        "experienceLeyLineEnabled");
        assertThat(module.defaultSettings("102550550"))
                .containsEntry("talentDomainEnabled", true)
                .containsEntry("weaponDomainEnabled", true)
                .containsEntry("moraLeyLineEnabled", true)
                .containsEntry("experienceLeyLineEnabled", true);
    }

    @Test
    void currentBackendPortOverridesStoredFullyAutoCdApi() {
        CultivationModuleConfigEntity stored = new CultivationModuleConfigEntity();
        stored.setUid("102550550");
        stored.setModuleId(FullyAutoToolsExecutionModule.ID);
        stored.setEnabled(true);
        stored.setSettingsJson("{\"http_api\":\"http://127.0.0.1:18081/bgi/cron/next-timestamp/all\"}");

        CultivationModuleConfigMapper mapper = mock(CultivationModuleConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", FullyAutoToolsExecutionModule.ID)).thenReturn(Optional.empty());
        CultivationModuleConfigurationService service = new CultivationModuleConfigurationService(
                new CultivationModuleRegistry(List.of(new FullyAutoToolsExecutionModule())),
                mapper, new ObjectMapper(), reader);
        ReflectionTestUtils.setField(service, "serverPort", 8081);

        CultivationModuleConfiguration configuration = service.find(
                "102550550", FullyAutoToolsExecutionModule.ID);

        assertThat(configuration.settings().get("http_api"))
                .isEqualTo("http://127.0.0.1:8081/bgi/cron/next-timestamp/all");
    }

    @Test
    void newerBetterGiEditWinsOverTheStoredWebConfiguration() {
        Instant betterGiModifiedAt = Instant.parse("2026-08-26T02:00:00Z");
        CultivationModuleConfigEntity stored = storedGatherConfiguration(
                "网页队伍", betterGiModifiedAt.minusSeconds(60), true);
        CultivationModuleConfigMapper mapper = mapperReturning(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", CdAwareAutoGatherExecutionModule.ID)).thenReturn(Optional.of(
                new BetterGiInstalledScriptSettingsReader.InstalledScriptSettings(
                        Map.of("partyName", "BetterGI 队伍"), false, betterGiModifiedAt)));
        CultivationModuleConfigurationService service = service(mapper, reader);

        CultivationModuleConfiguration result = service.find(
                "102550550", CdAwareAutoGatherExecutionModule.ID);

        assertThat(result.settings()).containsEntry("partyName", "BetterGI 队伍");
        assertThat(result.enabled()).isFalse();
        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getSettingsJson()).contains("BetterGI 队伍");
    }

    @Test
    void newerBetterGiEditPreservesWebOnlyOptionMetadata() {
        Instant betterGiModifiedAt = Instant.parse("2026-08-26T02:00:00Z");
        CultivationModuleConfigEntity stored = new CultivationModuleConfigEntity();
        stored.setUid("102550550");
        stored.setModuleId(ScriptGroupSettingsExecutionModule.ID);
        stored.setEnabled(true);
        stored.setSettingsJson("""
                {"partyName":"网页队伍","managedPartyOptions":["保留队伍"],"hiddenPartyOptions":["隐藏队伍"]}
                """);
        stored.setUpdateTime(LocalDateTime.ofInstant(
                betterGiModifiedAt.minusSeconds(60), ZoneId.systemDefault()));
        CultivationModuleConfigMapper mapper = mapperReturning(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", ScriptGroupSettingsExecutionModule.ID)).thenReturn(Optional.of(
                new BetterGiInstalledScriptSettingsReader.InstalledScriptSettings(
                        Map.of("partyName", "BetterGI 队伍"), true, betterGiModifiedAt)));
        CultivationModuleConfigurationService service = new CultivationModuleConfigurationService(
                new CultivationModuleRegistry(List.of(new ScriptGroupSettingsExecutionModule())),
                mapper, new ObjectMapper(), reader);

        CultivationModuleConfiguration result = service.find(
                "102550550", ScriptGroupSettingsExecutionModule.ID);

        assertThat(result.settings())
                .containsEntry("partyName", "BetterGI 队伍")
                .containsEntry("managedPartyOptions", List.of("保留队伍"))
                .containsEntry("hiddenPartyOptions", List.of("隐藏队伍"));
    }

    @Test
    void newerWebEditWinsOverTheUidSpecificBetterGiConfiguration() {
        Instant betterGiModifiedAt = Instant.parse("2026-08-26T02:00:00Z");
        CultivationModuleConfigEntity stored = storedGatherConfiguration(
                "网页队伍", betterGiModifiedAt.plusSeconds(60), true);
        CultivationModuleConfigMapper mapper = mapperReturning(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", CdAwareAutoGatherExecutionModule.ID)).thenReturn(Optional.of(
                new BetterGiInstalledScriptSettingsReader.InstalledScriptSettings(
                        Map.of("partyName", "BetterGI 队伍"), false, betterGiModifiedAt)));
        CultivationModuleConfigurationService service = service(mapper, reader);

        CultivationModuleConfiguration result = service.find(
                "102550550", CdAwareAutoGatherExecutionModule.ID);

        assertThat(result.settings()).containsEntry("partyName", "网页队伍");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void equalModificationTimesKeepTheStoredWebConfiguration() {
        Instant modifiedAt = Instant.parse("2026-08-26T02:00:00Z");
        CultivationModuleConfigEntity stored = storedGatherConfiguration("网页队伍", modifiedAt, true);
        CultivationModuleConfigMapper mapper = mapperReturning(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", CdAwareAutoGatherExecutionModule.ID)).thenReturn(Optional.of(
                new BetterGiInstalledScriptSettingsReader.InstalledScriptSettings(
                        Map.of("partyName", "BetterGI 队伍"), false, modifiedAt)));

        CultivationModuleConfiguration result = service(mapper, reader).find(
                "102550550", CdAwareAutoGatherExecutionModule.ID);

        assertThat(result.settings()).containsEntry("partyName", "网页队伍");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void webSaveAdvancesTheStoredTimestampPastThePreviouslyNewerBetterGiFile() {
        Instant fileModifiedAt = Instant.parse("2026-08-25T00:01:00Z");
        CultivationModuleConfigEntity initial = storedGatherConfiguration(
                "旧网页队伍", fileModifiedAt.minusSeconds(60), true);
        AtomicReference<CultivationModuleConfigEntity> stored = new AtomicReference<>(initial);
        CultivationModuleConfigMapper mapper = mock(CultivationModuleConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(ignored -> stored.get());
        when(mapper.updateById(any(CultivationModuleConfigEntity.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read("102550550", CdAwareAutoGatherExecutionModule.ID)).thenReturn(Optional.of(
                new BetterGiInstalledScriptSettingsReader.InstalledScriptSettings(
                        Map.of("partyName", "BetterGI 队伍"), true, fileModifiedAt)));
        CultivationModuleConfigurationService service = service(mapper, reader);

        CultivationModuleConfiguration result = service.save(
                "102550550", CdAwareAutoGatherExecutionModule.ID,
                new CultivationModuleConfigurationRequest(true, Map.of("partyName", "新网页队伍")));

        assertThat(result.settings()).containsEntry("partyName", "新网页队伍");
        assertThat(stored.get().getUpdateTime().atZone(ZoneId.systemDefault()).toInstant())
                .isAfter(fileModifiedAt);
    }

    private static CultivationModuleConfigEntity storedGatherConfiguration(
            String partyName, Instant modifiedAt, boolean enabled) {
        CultivationModuleConfigEntity stored = new CultivationModuleConfigEntity();
        stored.setUid("102550550");
        stored.setModuleId(CdAwareAutoGatherExecutionModule.ID);
        stored.setEnabled(enabled);
        stored.setSettingsJson("{\"partyName\":\"" + partyName + "\"}");
        stored.setUpdateTime(LocalDateTime.ofInstant(modifiedAt, ZoneId.systemDefault()));
        return stored;
    }

    private static CultivationModuleConfigMapper mapperReturning(CultivationModuleConfigEntity stored) {
        CultivationModuleConfigMapper mapper = mock(CultivationModuleConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        return mapper;
    }

    private static CultivationModuleConfigurationService service(
            CultivationModuleConfigMapper mapper,
            BetterGiInstalledScriptSettingsReader reader) {
        return new CultivationModuleConfigurationService(
                new CultivationModuleRegistry(List.of(new CdAwareAutoGatherExecutionModule())),
                mapper, new ObjectMapper(), reader);
    }
}
