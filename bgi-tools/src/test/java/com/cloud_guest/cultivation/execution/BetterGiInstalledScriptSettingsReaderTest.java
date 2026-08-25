package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.ScriptGroupSettingsExecutionModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BetterGiInstalledScriptSettingsReaderTest {

    @TempDir
    Path temporaryRoot;

    @Test
    void readsOnlyTheExactUidSpecificGroupWithItsEnabledStateAndModificationTime() throws Exception {
        Path groups = temporaryRoot.resolve(Path.of("User", "ScriptGroup"));
        Files.createDirectories(groups);
        Files.writeString(groups.resolve("000-shared.json"), group("共享队伍", "Enabled"));
        Path uidGroup = groups.resolve("养成一条龙-102550550.json");
        Files.writeString(uidGroup, group("UID 队伍", "Disabled"));
        Files.writeString(groups.resolve("养成一条龙-999999999.json"), group("其他 UID 队伍", "Enabled"));
        Instant modifiedAt = Instant.parse("2026-08-26T01:00:00Z");
        Files.setLastModifiedTime(uidGroup, FileTime.from(modifiedAt));
        CultivationMaterialSourceCatalog catalog = mock(CultivationMaterialSourceCatalog.class);
        when(catalog.betterGiRoot()).thenReturn(temporaryRoot);
        BetterGiInstalledScriptSettingsReader reader =
                new BetterGiInstalledScriptSettingsReader(catalog, new ObjectMapper());

        BetterGiInstalledScriptSettingsReader.InstalledScriptSettings result = reader
                .read("102550550", CdAwareAutoGatherExecutionModule.ID)
                .orElseThrow();

        assertThat(result.settings()).containsEntry("partyName", "UID 队伍");
        assertThat(result.enabled()).isFalse();
        assertThat(result.modifiedAt()).isEqualTo(modifiedAt);
    }

    @Test
    void readsTheUidSpecificScriptGroupRootSettings() throws Exception {
        Path groups = temporaryRoot.resolve(Path.of("User", "ScriptGroup"));
        Files.createDirectories(groups);
        Files.writeString(groups.resolve("养成一条龙-102550550.json"), """
                {
                  "config": {
                    "pathingConfig": {
                      "enabled": true,
                      "partyName": "BetterGI 根配置队伍",
                      "autoFightConfig": {"strategyName": "自定义策略"}
                    },
                    "enableShellConfig": false
                  },
                  "projects": []
                }
                """);
        CultivationMaterialSourceCatalog catalog = mock(CultivationMaterialSourceCatalog.class);
        when(catalog.betterGiRoot()).thenReturn(temporaryRoot);
        BetterGiInstalledScriptSettingsReader reader =
                new BetterGiInstalledScriptSettingsReader(catalog, new ObjectMapper());

        BetterGiInstalledScriptSettingsReader.InstalledScriptSettings result = reader
                .read("102550550", ScriptGroupSettingsExecutionModule.ID)
                .orElseThrow();

        assertThat(result.settings())
                .containsEntry("pathingEnabled", true)
                .containsEntry("partyName", "BetterGI 根配置队伍")
                .containsEntry("autoFightStrategyName", "自定义策略")
                .containsEntry("shellEnabled", false);
    }

    private static String group(String partyName, String status) {
        return """
                {
                  "name": "配置组",
                  "projects": [{
                    "folderName": "CD-Aware-AutoGather",
                    "status": "%s",
                    "jsScriptSettingsObject": {"partyName": "%s"}
                  }]
                }
                """.formatted(status, partyName);
    }
}
