package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.CultivationOcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
                [{"name":"镀金旅团·机弩兵","item":["织金红绸"],"tags":[]}]
                """);
        Files.createDirectories(temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "镀金旅团")));

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
        assertThat(catalog.findWeeklyBoss("无光涡眼")).contains("吞星之鲸");
    }
}
