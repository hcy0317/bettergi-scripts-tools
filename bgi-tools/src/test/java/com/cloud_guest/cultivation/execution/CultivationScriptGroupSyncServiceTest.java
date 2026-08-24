package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationScriptGroupSyncServiceTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void syncsMonsterRoutesOnlyToUidCultivationGroupAndLeavesSharedGroupsUntouched() throws Exception {
        Path scriptGroup = temporaryRoot.resolve(Path.of("User", "ScriptGroup", "每日进阶.json"));
        Files.createDirectories(scriptGroup.getParent());
        Files.writeString(scriptGroup, """
                {"projects":[{"folderName":"HCY-FullyAutoAndSemiAutoTools","jsScriptSettingsObject":{
                  "treeLevel_0_0":["锄地专区"],"treeLevel_1_1":["巡陆艇"],"unmanaged":"保留"
                }}]}
                """);
        Path cultivationGroup = scriptGroup.getParent().resolve("养成一条龙-102550550.json");
        Files.writeString(cultivationGroup, """
                {"projects":[{"folderName":"HCY-FullyAutoAndSemiAutoTools","jsScriptSettingsObject":{
                  "treeLevel_0_0":[],"treeLevel_1_1":[],"unmanaged":"专属保留"
                }}]}
                """);

        CultivationExecutionService executionService = mock(CultivationExecutionService.class);
        CultivationMaterialSourceCatalog catalog = mock(CultivationMaterialSourceCatalog.class);
        when(catalog.betterGiRoot()).thenReturn(temporaryRoot);
        when(executionService.projection("102550550")).thenReturn(projection());
        ObjectMapper objectMapper = new ObjectMapper();

        CultivationScriptSyncResult result = new CultivationScriptGroupSyncService(
                executionService, catalog, objectMapper)
                .sync("102550550", FullyAutoToolsExecutionModule.ID);

        JsonNode sharedSettings = objectMapper.readTree(scriptGroup.toFile())
                .path("projects").get(0).path("jsScriptSettingsObject");
        assertThat(texts(sharedSettings.path("treeLevel_0_0"))).containsExactly("锄地专区");
        assertThat(texts(sharedSettings.path("treeLevel_1_1"))).containsExactly("巡陆艇");
        assertThat(sharedSettings.path("unmanaged").asText()).isEqualTo("保留");

        JsonNode settings = objectMapper.readTree(cultivationGroup.toFile())
                .path("projects").get(0).path("jsScriptSettingsObject");
        assertThat(texts(settings.path("treeLevel_0_0")))
                .containsExactly("敌人与魔物");
        assertThat(texts(settings.path("treeLevel_1_1")))
                .containsExactly("蕈兽", "镀金旅团");
        assertThat(settings.path("unmanaged").asText()).isEqualTo("专属保留");
        assertThat(settings.path("cd_open").asBoolean()).isTrue();
        assertThat(result.updatedTasks()).isEqualTo(1);
        assertThat(result.scriptGroupFiles()).containsExactly("养成一条龙-102550550.json");
        try (var backups = Files.walk(Path.of(result.backupDirectory()))) {
            assertThat(backups.anyMatch(path -> path.getFileName().toString().equals("养成一条龙-102550550.json")))
                    .isTrue();
        }
    }

    private static CultivationExecutionProjection projection() {
        CultivationExecutionProjection.GatherAction gather = new CultivationExecutionProjection.GatherAction(
                "CD-Aware-AutoGather", "无缺口", Map.of(), List.of());
        CultivationExecutionProjection.MonsterAction monster = new CultivationExecutionProjection.MonsterAction(
                "FullyAutoAndSemiAutoTools", "待执行",
                Map.of("key", "PGCSBY37NJA", "config_run", "执行", "open_cd", true,
                        "routeFamilies", List.of("蕈兽", "镀金旅团")),
                List.of(), List.of("蕈兽", "镀金旅团"));
        return new CultivationExecutionProjection(
                "102550550", 1, "IMPORTED", "单轮执行", List.of(), List.of(), List.of(), gather, monster,
                List.of(), new CultivationExecutionPreferences(
                        "102550550", "", "", "", true), List.of());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
