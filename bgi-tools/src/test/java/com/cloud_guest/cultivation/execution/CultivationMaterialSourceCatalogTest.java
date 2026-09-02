package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.CultivationOcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationMaterialSourceCatalogTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void resolvesMonsterDropsThroughInstalledCatalogAndRouteDirectories() throws Exception {
        Path asset = temporaryRoot.resolve(Path.of(
                "User", "JsScript", "AutoHoeingOneDragon", "assets", "monsterInfo.json"));
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, """
                [
                  {"name":"镀金旅团·机弩兵","item":["织金红绸"],"tags":[]},
                  {"name":"凝冰戍卫","item":["幻造晶鳞石"],"tags":[]},
                  {"name":"嵌合角熊","item":["并生嵌合胞"],"tags":[]},
                  {"name":"兽怪捷掠者","item":["沉积增生物"],"tags":[]},
                  {"name":"幼嫩的分节树","item":["灵生分蘖节"],"tags":[]}
                ]
                """);
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "镀金旅团")));
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "肌生晶石的妖精")));
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "异种合成魔兽")));
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "兽怪暴徒")));
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "荒野树妖")));
        Path specialtyRoute = temporaryRoot.resolve(Path.of(
                "User", "AutoPathing", "地方特产", "挪德卡莱", "月矩力结晶", "路线", "01.json"));
        Files.createDirectories(specialtyRoute.getParent());
        Files.writeString(specialtyRoute, "{}");

        CultivationOcrProperties properties = new CultivationOcrProperties();
        properties.setBettergiRoot(temporaryRoot.toString());
        CultivationMaterialSourceCatalog catalog = new CultivationMaterialSourceCatalog(
                properties, new ObjectMapper());

        assertThat(catalog.findMonster("织金红绸")).get()
                .extracting(CultivationMaterialSourceCatalog.MonsterSource::routeFamily)
                .isEqualTo("镀金旅团");
        assertThat(catalog.findBoss("谜土的护符")).get()
                .extracting(CultivationMaterialSourceCatalog.BossSource::bossName)
                .isEqualTo("灵觉隐修的迷者");
        assertThat(catalog.findBoss("焰中不灭花枝")).get()
                .extracting(CultivationMaterialSourceCatalog.BossSource::bossName)
                .isEqualTo("不灭衍生造物");
        assertThat(catalog.findWeeklyBoss("无光涡眼")).contains("吞星之鲸");
        assertThat(catalog.findWeeklyBoss("扭曲的枯枝")).contains("世界树博士");
        assertThat(catalog.findMonster("幻造晶鳞石")).get()
                .extracting(CultivationMaterialSourceCatalog.MonsterSource::routeFamily)
                .isEqualTo("肌生晶石的妖精");
        assertThat(catalog.findMonster("并生嵌合胞")).get()
                .extracting(CultivationMaterialSourceCatalog.MonsterSource::routeFamily)
                .isEqualTo("异种合成魔兽");
        assertThat(catalog.findMonster("沉积增生物")).get()
                .extracting(CultivationMaterialSourceCatalog.MonsterSource::routeFamily)
                .isEqualTo("兽怪暴徒");
        assertThat(catalog.findMonster("灵生分蘖节")).get()
                .extracting(CultivationMaterialSourceCatalog.MonsterSource::routeFamily)
                .isEqualTo("荒野树妖");
        assertThat(catalog.findSpecialtyCountry("月矩力结晶")).contains("挪德卡莱");
    }

    @Test
    void discoversRunningBetterGiInstallationWithoutConfiguredRoot() throws Exception {
        Path betterGiRoot = temporaryRoot.resolve("BetterGI");
        Files.createDirectories(betterGiRoot.resolve(Path.of("User", "ScriptGroup")));
        Path executable = Files.createFile(betterGiRoot.resolve("BetterGI.exe"));

        assertThat(CultivationMaterialSourceCatalog.resolveBetterGiRoot(
                "",
                temporaryRoot.resolve("source-checkout"),
                List.of(executable),
                List.of()))
                .contains(betterGiRoot.toAbsolutePath().normalize());
    }

    @Test
    void discoversKnownInstallationCandidateWhenBetterGiIsNotRunning() throws Exception {
        Path betterGiRoot = temporaryRoot.resolve("installed");
        Files.createDirectories(betterGiRoot.resolve(Path.of("User", "ScriptGroup")));
        Files.createFile(betterGiRoot.resolve("BetterGI.exe"));

        assertThat(CultivationMaterialSourceCatalog.resolveBetterGiRoot(
                "",
                temporaryRoot.resolve("source-checkout"),
                List.of(),
                List.of(betterGiRoot)))
                .contains(betterGiRoot.toAbsolutePath().normalize());
    }

    @Test
    void knownRootShortCircuitsProcessAndInstallationDiscovery() throws Exception {
        Path betterGiRoot = temporaryRoot.resolve("configured");
        Files.createDirectories(betterGiRoot);
        AtomicInteger processScans = new AtomicInteger();
        AtomicInteger installationScans = new AtomicInteger();

        assertThat(CultivationMaterialSourceCatalog.resolveBetterGiRoot(
                betterGiRoot.toString(),
                temporaryRoot.resolve("source-checkout"),
                () -> {
                    processScans.incrementAndGet();
                    return List.of();
                },
                () -> {
                    installationScans.incrementAndGet();
                    return List.of();
                }))
                .contains(betterGiRoot.toAbsolutePath().normalize());
        assertThat(processScans).hasValue(0);
        assertThat(installationScans).hasValue(0);
    }
}
