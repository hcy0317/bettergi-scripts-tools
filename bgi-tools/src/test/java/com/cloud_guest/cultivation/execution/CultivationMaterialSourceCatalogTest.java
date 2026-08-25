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
        assertThat(catalog.findWeeklyBoss("无光涡眼")).contains("吞星之鲸");
        assertThat(catalog.findSpecialtyCountry("月矩力结晶")).contains("挪德卡莱");
    }
}
