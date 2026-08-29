package com.cloud_guest.cultivation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BetterGiCombatOptionCatalogTest {

    @TempDir
    Path temporaryRoot;

    @Test
    void discoversExistingPartiesAndCombatStrategiesFromTheInstalledTree() throws Exception {
        Path autoFight = temporaryRoot.resolve(Path.of("User", "AutoFight"));
        Path groups = temporaryRoot.resolve(Path.of("User", "ScriptGroup"));
        Files.createDirectories(autoFight);
        Files.createDirectories(groups);
        Files.writeString(autoFight.resolve("00-钟心那万.txt"), "钟离 skill()\n");
        Files.writeString(autoFight.resolve("自定义首领.json"), "{}\n");
        Files.writeString(autoFight.resolve("说明.md"), "ignored\n");
        Files.writeString(temporaryRoot.resolve(Path.of("User", "config.json")), """
                {
                  "AutoDomainConfig": {"PartyName": "速通队"},
                  "AutoBossConfig": {"TeamName": "首领队", "StrategyName": "00-钟心那万"}
                }
                """);
        Files.writeString(groups.resolve("养成一条龙-102550550.json"), """
                {
                  "config": {
                    "pathingConfig": {
                      "partyName": "钟心那万",
                      "autoFightConfig": {"strategyName": "根据队伍自动选择", "teamNames": "钟离,心海,那维莱特,万叶"}
                    }
                  },
                  "projects": [{
                    "folderName": "AutoPlan",
                    "jsScriptSettingsObject": {"bossPartyName": "养成首领队", "bossStrategyName": "自定义首领"}
                  }]
                }
                """);
        CultivationMaterialSourceCatalog sourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        when(sourceCatalog.betterGiRoot()).thenReturn(temporaryRoot);

        BetterGiCombatOptionCatalog.Options result =
                new BetterGiCombatOptionCatalog(sourceCatalog, new ObjectMapper()).discover();

        assertThat(result.parties()).containsExactly("养成首领队", "速通队", "钟心那万", "首领队");
        assertThat(result.parties()).doesNotContain("钟离,心海,那维莱特,万叶");
        assertThat(result.strategies()).containsExactly(
                "根据队伍自动选择", "00-钟心那万", "自定义首领");
    }
}
