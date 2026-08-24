package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfiguration;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.execution.module.CultivationModuleDefinition;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.ScriptGroupSettingsExecutionModule;
import com.cloud_guest.cultivation.execution.module.WeeklyBossExecutionModule;
import com.cloud_guest.service.AutoPlanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationOneStopServiceTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void generatesDedicatedGroupFromOnlyNeededModules() throws Exception {
        Path source = temporaryRoot.resolve(Path.of("User", "ScriptGroup", "来源组.json"));
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                {"index":1,"name":"来源组","config":{"pathingConfig":{"partyName":"通用队伍"}},"projects":[
                  {"name":"体力","folderName":"AutoPlan","type":"Javascript","jsScriptSettingsObject":{}},
                  {"name":"采集","folderName":"CD-Aware-AutoGather","type":"Javascript","jsScriptSettingsObject":{}},
                  {"name":"怪物","folderName":"HCY-FullyAutoAndSemiAutoTools","type":"Javascript","jsScriptSettingsObject":{}}
                ]}
                """);
        Path uidSettings = temporaryRoot.resolve(Path.of("User", "JsScript",
                "HCY-FullyAutoAndSemiAutoTools", "config", "uidSettings.json"));
        Files.createDirectories(uidSettings.getParent());
        Files.writeString(uidSettings, """
                [["102550550",[
                  {"name":"treeLevel_2_9","label":"《敌人与魔物》->[镀金旅团]",
                   "options":["镀金旅团路线甲","镀金旅团路线乙"]}
                ]]]
                """);
        Files.writeString(uidSettings.getParent().getParent().resolve("settings.json"), "[]");
        Path gatherScript = temporaryRoot.resolve(Path.of("User", "JsScript", "CD-Aware-AutoGather"));
        Files.createDirectories(gatherScript);
        Files.writeString(gatherScript.resolve("settings.json"), "[]");
        Path safeGatherRoute = temporaryRoot.resolve(Path.of("User", "AutoPathing", "地方特产", "须弥",
                "沙脂蛹", "1. 高成功率路线", "沙脂蛹-神的棋盘.json"));
        Path unsafeGatherRoute = temporaryRoot.resolve(Path.of("User", "AutoPathing", "地方特产", "须弥",
                "沙脂蛹", "2. 低成功率路线", "沙脂蛹-折胫谷.json"));
        Files.createDirectories(safeGatherRoute.getParent());
        Files.createDirectories(unsafeGatherRoute.getParent());
        Files.writeString(safeGatherRoute, "{}");
        Files.writeString(unsafeGatherRoute, "{}");

        CultivationExecutionService executionService = mock(CultivationExecutionService.class);
        CultivationModuleConfigurationService configurationService = mock(CultivationModuleConfigurationService.class);
        CultivationMaterialSourceCatalog catalog = mock(CultivationMaterialSourceCatalog.class);
        AutoPlanService autoPlanService = mock(AutoPlanService.class);
        when(catalog.betterGiRoot()).thenReturn(temporaryRoot);
        when(executionService.projection("102550550")).thenReturn(projection());
        when(configurationService.find("102550550", AutoPlanResinExecutionModule.ID)).thenReturn(
                configuration(AutoPlanResinExecutionModule.ID, Map.of(
                        "auto_load", List.of("bgi_tools加载"),
                        "leyLineCountry", "挪德卡莱",
                        "talentDomainEnabled", false,
                        "moraLeyLineEnabled", true,
                        "experienceLeyLineEnabled", false)));
        when(configurationService.find("102550550", CdAwareAutoGatherExecutionModule.ID)).thenReturn(
                configuration(CdAwareAutoGatherExecutionModule.ID, Map.of()));
        when(configurationService.find("102550550", FullyAutoToolsExecutionModule.ID)).thenReturn(
                configuration(FullyAutoToolsExecutionModule.ID, Map.of("open_cd", true)));
        when(configurationService.find("102550550", WeeklyBossExecutionModule.ID)).thenReturn(
                configuration(WeeklyBossExecutionModule.ID, Map.of("unfairContractTerms", true)));
        when(configurationService.find("102550550", ScriptGroupSettingsExecutionModule.ID)).thenReturn(
                configuration(ScriptGroupSettingsExecutionModule.ID, Map.of(
                        "partyName", "养成队伍",
                        "autoPickEnabled", true,
                        "autoFightEnabled", true,
                        "autoFightStrategyName", "根据队伍自动选择",
                        "taskCycleEnabled", false,
                        "shellEnabled", false,
                        "shellTimeoutSeconds", 90)));

        CultivationOneStopService service = new CultivationOneStopService(
                executionService, configurationService, catalog, autoPlanService, new ObjectMapper());
        CultivationOneStopResult result = service.prepare("102550550");

        assertThat(result.autoPlanActions()).isEqualTo(2);
        assertThat(result.scriptTasks()).isEqualTo(4);
        assertThat(result.scriptGroupName()).isEqualTo("养成一条龙-102550550");
        JsonNode group = new ObjectMapper().readTree(Path.of(result.scriptGroupFile()).toFile());
        assertThat(group.path("name").asText()).isEqualTo("养成一条龙-102550550");
        assertThat(StreamSupport.stream(group.path("projects").spliterator(), false)
                .map(project -> project.path("folderName").asText()).toList())
                .containsExactly("AutoPlan", "CD-Aware-AutoGather",
                        "HCY-FullyAutoAndSemiAutoTools", "WeeklyBoss");
        assertThat(StreamSupport.stream(group.path("projects").spliterator(), false)
                .map(project -> project.path("name").asText()).toList())
                .containsExactly("养成体力：摩拉·世界首领", "养成采集：沙脂蛹",
                        "养成怪物：镀金旅团", "周本 - 博士");
        JsonNode autoPlanSettings = group.path("projects").get(0).path("jsScriptSettingsObject");
        assertThat(autoPlanSettings.path("bgi_tools_http_pull_json_config").asText())
                .isEqualTo("http://127.0.0.1:18081/bgi/auto/plan/json");
        JsonNode gatherSettings = group.path("projects").get(1).path("jsScriptSettingsObject");
        assertThat(gatherSettings.path("selectForgingOre")).isEmpty();
        assertThat(gatherSettings.path("filterPathByKeywords").asText()).isEmpty();
        assertThat(gatherSettings.path("selectLocalSpecialty_须弥").get(0).asText()).isEqualTo("沙脂蛹");
        assertThat(StreamSupport.stream(gatherSettings.path("selectRoute_沙脂蛹").spliterator(), false)
                .map(JsonNode::asText).toList()).containsExactly("1. 高成功率路线");
        JsonNode monsterSettings = group.path("projects").get(2).path("jsScriptSettingsObject");
        assertThat(monsterSettings.path("treeLevel_0_0").get(0).asText()).isEqualTo("敌人与魔物");
        assertThat(monsterSettings.path("treeLevel_1_1").get(0).asText()).isEqualTo("镀金旅团");
        assertThat(StreamSupport.stream(monsterSettings.path("treeLevel_2_9").spliterator(), false)
                .map(JsonNode::asText).toList())
                .containsExactly("镀金旅团路线甲", "镀金旅团路线乙");
        assertThat(monsterSettings.path("http_api").asText())
                .isEqualTo("http://127.0.0.1:18081/bgi/cron/next-timestamp/all");
        assertThat(group.path("config").path("pathingConfig").path("partyName").asText())
                .isEqualTo("养成队伍");
        assertThat(group.path("config").path("pathingConfig").path("autoPickEnabled").asBoolean()).isTrue();
        assertThat(group.path("config").path("pathingConfig").path("autoFightConfig")
                .path("strategyName").asText()).isEqualTo("根据队伍自动选择");
        assertThat(group.path("config").path("pathingConfig").path("taskCycleConfig")
                .path("enable").asBoolean()).isFalse();
        assertThat(group.path("config").path("enableShellConfig").asBoolean()).isFalse();
        assertThat(group.path("config").path("shellConfig").path("timeout").asInt()).isEqualTo(90);
        JsonNode gatherUi = new ObjectMapper().readTree(gatherScript.resolve("settings.json").toFile());
        assertThat(StreamSupport.stream(gatherUi.spliterator(), false)
                .map(field -> field.path("name").asText()).toList())
                .contains("selectLocalSpecialty_须弥", "selectRoute_沙脂蛹");
        JsonNode monsterUi = new ObjectMapper().readTree(
                uidSettings.getParent().getParent().resolve("settings.json").toFile());
        assertThat(StreamSupport.stream(monsterUi.spliterator(), false)
                .map(field -> field.path("name").asText()).toList())
                .contains("treeLevel_2_9");

        Files.delete(source);
        Path duplicate = source.getParent().resolve("养成一条龙-102550550-R0-旧副本.json");
        Files.copy(Path.of(result.scriptGroupFile()), duplicate);
        CultivationOneStopResult repeated = service.prepare("102550550");
        assertThat(repeated.scriptGroupName()).isEqualTo(result.scriptGroupName());
        assertThat(new ObjectMapper().readTree(Path.of(repeated.scriptGroupFile()).toFile())
                .path("projects").size()).isEqualTo(4);
        assertThat(duplicate).doesNotExist();
        assertThat(Path.of(repeated.backupDirectory()).resolve("ScriptGroup")
                .resolve(duplicate.getFileName())).exists();
        try (var groups = Files.list(source.getParent())) {
            assertThat(groups.filter(path -> path.getFileName().toString()
                    .startsWith("养成一条龙-102550550")).toList()).hasSize(1);
        }

        CultivationLaunchResult launch = service.start("102550550");
        URI launchUri = URI.create(launch.launchUri());
        assertThat(launchUri.getScheme()).isEqualToIgnoringCase("BetterGICultivation");
        assertThat(launchUri.getHost()).isEqualTo("one-stop");
        assertThat(launch.message()).contains("BetterGI 宿主");
        String requestToken = launchUri.getQuery().replaceFirst("^request=", "");
        assertThat(requestToken).matches("[0-9a-f-]{36}");
        Path launchRequest = temporaryRoot.resolve(Path.of(
                "User", "launch-requests", "cultivation-one-stop", requestToken + ".json"));
        JsonNode request = new ObjectMapper().readTree(launchRequest.toFile());
        assertThat(request.path("version").asInt()).isEqualTo(1);
        assertThat(request.path("kind").asText()).isEqualTo("cultivation-one-stop");
        assertThat(request.path("uid").asText()).isEqualTo("102550550");
        assertThat(request.path("scriptGroupName").asText()).isEqualTo("养成一条龙-102550550");
        assertThat(Instant.parse(request.path("expiresAtUtc").asText())).isAfter(Instant.now());
    }

    private static CultivationExecutionProjection projection() {
        var resin = new CultivationExecutionProjection.ResinAction(
                "「浪迹」的指引", 60, "秘境", "无光的深都", "天赋", "速通",
                "可生成下一步行动", 3, "「浪迹」的哲学", List.of(0, 3, 6));
        var mora = new CultivationExecutionProjection.ResinAction(
                "摩拉", 750_000, "地脉", "藏金之花", "经验与摩拉", "速通",
                "可生成下一步行动", null, "摩拉", List.of());
        var experience = new CultivationExecutionProjection.ResinAction(
                "大英雄的经验", 100, "地脉", "启示之花", "经验与摩拉", "速通",
                "可生成下一步行动", null, "大英雄的经验", List.of());
        var boss = new CultivationExecutionProjection.BossAction(
                "谜土的护符", 45, "灵觉隐修的迷者", "纳塔", "速通", Map.of(), "待执行");
        var weekly = new CultivationExecutionProjection.WeeklyBossAction(
                "狂人的约束", 18, "博士", Map.of("monsterName", "博士", "unfairContractTerms", true), "待执行");
        var gatherTarget = new CultivationExecutionProjection.GatherTarget(
                "沙脂蛹", 168, 4, 164, "须弥", "selectLocalSpecialty_须弥");
        var gather = new CultivationExecutionProjection.GatherAction(
                "CD-Aware-AutoGather", "待执行",
                Map.of("runMode", "采集选中的材料",
                        "filterPathByKeywords", "晶蝶|水晶块|400精英",
                        "selectLocalSpecialty_须弥", List.of("沙脂蛹"),
                        "selectForgingOre", List.of("水晶块")),
                List.of(gatherTarget));
        var monsterTarget = new CultivationExecutionProjection.MonsterTarget(
                "织金红绸", 18, 0, 18, "镀金旅团", List.of("镀金旅团·机弩兵"));
        var monster = new CultivationExecutionProjection.MonsterAction(
                "FullyAutoAndSemiAutoTools", "待执行",
                Map.of("open_cd", true, "routeFamilies", List.of("镀金旅团"),
                        "treeLevel_0_0", List.of("锄地专区", "敌人与魔物"),
                        "treeLevel_1_0", List.of("精英400@汐"),
                        "treeLevel_1_1", List.of("巡陆艇"),
                        "config_white_list", "锄地专区"),
                List.of(monsterTarget), List.of("镀金旅团"));
        return new CultivationExecutionProjection(
                "102550550", 1, "IMPORTED", "单轮执行", List.of(resin, mora, experience), List.of(boss),
                List.of(weekly), gather, monster, List.of(),
                new CultivationExecutionPreferences("102550550", "速通", "采集", "采集", true),
                List.of("速通", "采集"));
    }

    private static CultivationModuleConfiguration configuration(String id, Map<String, Object> settings) {
        return new CultivationModuleConfiguration(
                new CultivationModuleDefinition(id, id, "1.0", "", "", List.of(), List.of()),
                true, settings);
    }
}
