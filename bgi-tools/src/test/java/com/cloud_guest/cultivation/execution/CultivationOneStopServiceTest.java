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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CultivationOneStopServiceTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void rejectsPathTraversalUidBeforeResolvingOrWritingTheScriptGroup() throws Exception {
        CultivationExecutionService executionService = mock(CultivationExecutionService.class);
        CultivationOneStopService service = new CultivationOneStopService(
                executionService, mock(CultivationModuleConfigurationService.class),
                mock(CultivationMaterialSourceCatalog.class), mock(AutoPlanService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.prepare("..\\..\\outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字");
        verifyNoInteractions(executionService);
        assertThat(regularFileCount(temporaryRoot)).isZero();
    }

    @Test
    void doesNotFallBackToKnownUnsafeGatherRoutes() {
        assertThat(CultivationOneStopService.safeGatherRouteNames(List.of(
                "低成功率路线", "2. 低效", "暂不可用"))).isEmpty();
    }

    @Test
    void craftOnlyProjectionStillRequiresThePlanDrivenAutoPlanTask() {
        CultivationExecutionProjection current = projection();
        CultivationExecutionProjection craftOnly = new CultivationExecutionProjection(
                current.uid(), current.revision(), "NEEDS_CRAFT", current.executionMode(),
                List.of(new CultivationCraftingAction("「笃行」的指引", 1, "角色天赋素材")),
                List.of(), List.of(), List.of(),
                new CultivationExecutionProjection.GatherAction("gather", "无", Map.of(), List.of()),
                new CultivationExecutionProjection.MonsterAction("monster", "无", Map.of(), List.of(), List.of()),
                List.of(), current.preferences(), current.partyOptions());

        assertThat(CultivationOneStopService.hasPlanDrivenAction(
                craftOnly,
                configuration(AutoPlanResinExecutionModule.ID, Map.of()))).isTrue();
    }

    @Test
    void generatesDedicatedGroupFromOnlyNeededModules() throws Exception {
        Path source = temporaryRoot.resolve(Path.of("User", "ScriptGroup", "来源组.json"));
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                {"index":1,"name":"来源组","config":{"pathingConfig":{"partyName":"通用队伍","autoFightConfig":{"guardianAvatar":"4","burstEnabled":true}}},"projects":[
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
        Path routeA = temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "镀金旅团",
                "镀金旅团路线甲", "路线甲.json"));
        Path routeB = temporaryRoot.resolve(Path.of("User", "AutoPathing", "敌人与魔物", "镀金旅团",
                "镀金旅团路线乙", "路线乙.json"));
        Files.createDirectories(routeA.getParent());
        Files.createDirectories(routeB.getParent());
        Files.writeString(routeA, "{\"positions\":[{\"action\":\"fight\"}]}");
        Files.writeString(routeB, "{\"positions\":[{\"action\":\"fight\"},{\"action\":\"fight\"},{\"action\":\"fight\"},{\"action\":\"fight\"}]}");
        Path directFamilyRoute = temporaryRoot.resolve(Path.of(
                "User", "AutoPathing", "敌人与魔物", "盗宝团", "璃月路线.json"));
        Files.createDirectories(directFamilyRoute.getParent());
        Files.writeString(directFamilyRoute, "{\"positions\":[{\"action\":\"fight\"}]}");
        Path mixedSafeRoute = temporaryRoot.resolve(Path.of(
                "User", "AutoPathing", "敌人与魔物", "混合族", "安全路线.json"));
        Path mixedUnsafeRoute = mixedSafeRoute.getParent().resolve("9. 低效路线.json");
        Files.createDirectories(mixedSafeRoute.getParent());
        Files.writeString(mixedSafeRoute, "{\"positions\":[{\"action\":\"fight\"}]}");
        Files.writeString(mixedUnsafeRoute, "{\"positions\":[{\"action\":\"fight\"}]}");
        Path gatherScript = temporaryRoot.resolve(Path.of("User", "JsScript", "CD-Aware-AutoGather"));
        Files.createDirectories(gatherScript);
        Files.writeString(gatherScript.resolve("settings.json"), "[]");
        Path autoPlanScript = temporaryRoot.resolve(Path.of("User", "JsScript", "AutoPlan"));
        Files.createDirectories(autoPlanScript.resolve("utils"));
        Files.writeString(autoPlanScript.resolve("main.js"), """
                import {buildInitConfigSettings, config, initConfig, initSettings} from './config/config';
                async function main() {
                    // 初始化配置
                    await init();
                    let runConfig = config.run.config;
                }
                await main();
                """);
        Path betterGiConfig = temporaryRoot.resolve(Path.of("User", "config.json"));
        Files.createDirectories(betterGiConfig.getParent());
        Files.writeString(betterGiConfig, "{\"autoFightConfig\":{\"burstEnabled\":false,\"pickDropsAfterFightSeconds\":60}}");
        Files.writeString(autoPlanScript.resolve("utils").resolve("load_check_run.js"), """
                async function runDomain(domainParam) {
                    await dispatcher.RunAutoDomainTask(domainParam);
                }
                async function runBoss(param) {
                    await dispatcher.RunAutoBossTask(param)
                }
                """);
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
                        "bgi_tools_token", "Authorization= ",
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

        assertThat(result.autoPlanActions()).isEqualTo(1);
        assertThat(result.message()).contains("计划驱动");
        assertThat(result.scriptTasks()).isEqualTo(5);
        assertThat(result.scriptGroupName()).isEqualTo("养成一条龙-102550550");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("混合族") && warning.contains("没有有效候选"));
        JsonNode group = new ObjectMapper().readTree(Path.of(result.scriptGroupFile()).toFile());
        assertThat(group.path("name").asText()).isEqualTo("养成一条龙-102550550");
        assertThat(StreamSupport.stream(group.path("projects").spliterator(), false)
                .map(project -> project.path("folderName").asText()).toList())
                .containsExactly("AutoPlan", "CD-Aware-AutoGather",
                        "HCY-FullyAutoAndSemiAutoTools", "WeeklyBoss", "AutoPlan");
        assertThat(StreamSupport.stream(group.path("projects").spliterator(), false)
                .map(project -> project.path("name").asText()).toList())
                .containsExactly("养成体力：摩拉·世界首领", "养成采集：沙脂蛹",
                        "养成怪物：镀金旅团·盗宝团", "周本 - 博士", "养成收尾：权威库存复核");
        JsonNode autoPlanSettings = group.path("projects").get(0).path("jsScriptSettingsObject");
        assertThat(autoPlanSettings.path("bgi_tools_http_pull_json_config").asText())
                .isEqualTo("http://127.0.0.1:18081/bgi/auto/plan/json");
        assertThat(autoPlanSettings.path("cultivation_plan_mode").asBoolean()).isTrue();
        assertThat(autoPlanSettings.path("talentDomainEnabled").asBoolean()).isFalse();
        assertThat(autoPlanSettings.path("moraLeyLineEnabled").asBoolean()).isTrue();
        assertThat(autoPlanSettings.path("run_config").asText()).isEmpty();
        assertThat(autoPlanSettings.path("auto_check")).isEmpty();
        assertThat(autoPlanSettings.path("bgi_tools_token").asText()).isEmpty();
        assertThat(autoPlanScript.resolve("utils").resolve("cultivation_plan.js")).exists();
        String cultivationPlanSource = Files.readString(
                autoPlanScript.resolve("utils").resolve("cultivation_plan.js"));
        assertThat(cultivationPlanSource)
                .contains("config.run.exclude_run_exception = false")
                .contains("config.run.loop_plan = false")
                .contains("GridScreenName.CharacterDevelopmentItems")
                .contains("targets.materialNamesByGrid")
                .contains("for (const [gridScreenName, namesValue] of Object.entries(grouped))")
                .contains("await countInventoryItems(names, gridScreenName)")
                .contains("const retryNames = names.filter")
                .contains("首次识别未知，仅对缺失项再复查一次")
                .contains("未知项将保留上次可信库存")
                .contains("async function runInventoryReconcileOnce(config, state, reason)")
                .contains("const inventoryReconcileState = {attempted: false};")
                .contains("if (state.attempted)")
                .contains("本轮已完成一次完整库存复核，不再重复检查")
                .contains("action.actionType === \"CRAFT\"")
                .contains("await genshin.GoToCraftingBench(action.craftCountry)")
                .doesNotContain("await genshin.GoCraftResin(action.craftCountry)")
                .contains("await genshin.CraftMaterial(")
                .contains("完整库存复核后仍未开放行动")
                .contains("return response.status === \"REPLANNING\"")
                .containsOnlyOnce("if (action.status === \"PLAN_NEEDS_RECONCILE\")")
                .contains("return result.status === \"REPLANNING\"")
                .doesNotContain("config, inventoryReconcileState, `合成 ${action.materialName} 后复核`")
                .containsPattern("(?s)if \\(action\\.actionType === \\\"CRAFT\\\"\\).*?"
                        + "executeCraftAction.*?runCultivationInventoryReconcile\\(config\\).*?continue;")
                .doesNotContain("param.GridScreenName = GridScreenName.Materials")
                .doesNotContain("gridScreenName: \"Materials\"")
                .doesNotContain("组末库存存在未知值，已停止后续执行")
                .doesNotContain("重新导入或人工确认")
                .doesNotContain("停止并等待完整重新清点")
                .doesNotContain("batchCompleted ? 0 : -1")
                .doesNotContain("}        const shouldContinue = await executeAction(")
                .containsPattern("(?s)if \\(targets\\.status === \\\"BUSY\\\"\\).*?return false;")
                .containsPattern("(?s)if \\(materialNames\\.length === 0\\).*?return true;");
        assertThat(Files.readString(autoPlanScript.resolve("utils").resolve("load_check_run.js")))
                .contains("return await dispatcher.RunAutoDomainTask(domainParam);")
                .contains("return await dispatcher.RunAutoBossTask(param)");
        assertThat(Files.readString(autoPlanScript.resolve("main.js")))
                .contains("runPlanDrivenCultivation")
                .contains("runCultivationInventoryReconcile")
                .contains("settings.cultivation_plan_mode")
                .contains("settings.cultivation_inventory_reconcile_mode");
        JsonNode gatherSettings = group.path("projects").get(1).path("jsScriptSettingsObject");
        assertThat(gatherSettings.path("selectForgingOre")).isEmpty();
        assertThat(gatherSettings.path("filterPathByKeywords").asText()).isEmpty();
        assertThat(gatherSettings.path("selectLocalSpecialty_须弥").get(0).asText()).isEqualTo("沙脂蛹");
        assertThat(StreamSupport.stream(gatherSettings.path("selectRoute_沙脂蛹").spliterator(), false)
                .map(JsonNode::asText).toList()).containsExactly("1. 高成功率路线");
        JsonNode monsterSettings = group.path("projects").get(2).path("jsScriptSettingsObject");
        assertThat(monsterSettings.path("treeLevel_0_0").get(0).asText()).isEqualTo("敌人与魔物");
        assertThat(StreamSupport.stream(monsterSettings.path("treeLevel_1_1").spliterator(), false)
                .map(JsonNode::asText).toList()).containsExactly("镀金旅团", "盗宝团");
        assertThat(StreamSupport.stream(monsterSettings.path("treeLevel_2_9").spliterator(), false)
                .map(JsonNode::asText).toList())
                .containsExactly("镀金旅团路线乙");
        assertThat(monsterSettings.path("http_api").asText())
                .isEqualTo("http://127.0.0.1:18081/bgi/cron/next-timestamp/all");
        JsonNode reconcileSettings = group.path("projects").get(4).path("jsScriptSettingsObject");
        assertThat(reconcileSettings.path("cultivation_inventory_reconcile_mode").asBoolean()).isTrue();
        assertThat(reconcileSettings.path("cultivation_plan_mode").asBoolean()).isFalse();
        assertThat(group.path("config").path("pathingConfig").path("partyName").asText())
                .isEqualTo("养成队伍");
        assertThat(group.path("config").path("pathingConfig").path("autoPickEnabled").asBoolean()).isTrue();
        assertThat(group.path("config").path("pathingConfig").path("autoFightConfig")
                .path("strategyName").asText()).isEqualTo("根据队伍自动选择");
        assertThat(group.path("config").path("pathingConfig").path("autoFightConfig")
                .path("burstEnabled").asBoolean()).isFalse();
        assertThat(group.path("config").path("pathingConfig").path("autoFightConfig")
                .path("pickDropsAfterFightSeconds").asInt()).isEqualTo(60);
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

        Path backupRoot = temporaryRoot.resolve(Path.of("User", "backup", "cultivation-one-stop"));
        byte[] groupBeforeRepeat = Files.readAllBytes(Path.of(result.scriptGroupFile()));
        byte[] gatherSettingsBeforeRepeat = Files.readAllBytes(gatherScript.resolve("settings.json"));
        Path monsterSettingsFile = uidSettings.getParent().getParent().resolve("settings.json");
        byte[] monsterSettingsBeforeRepeat = Files.readAllBytes(monsterSettingsFile);
        long backupFilesBeforeRepeat = regularFileCount(backupRoot);

        service.prepare("102550550");

        assertThat(Files.readAllBytes(Path.of(result.scriptGroupFile())))
                .isEqualTo(groupBeforeRepeat);
        assertThat(Files.readAllBytes(gatherScript.resolve("settings.json")))
                .isEqualTo(gatherSettingsBeforeRepeat);
        assertThat(Files.readAllBytes(monsterSettingsFile))
                .isEqualTo(monsterSettingsBeforeRepeat);
        assertThat(regularFileCount(backupRoot)).isEqualTo(backupFilesBeforeRepeat);

        Files.delete(source);
        Path duplicate = source.getParent().resolve("养成一条龙-102550550-R0-旧副本.json");
        Files.copy(Path.of(result.scriptGroupFile()), duplicate);
        CultivationOneStopResult repeated = service.prepare("102550550");
        assertThat(repeated.scriptGroupName()).isEqualTo(result.scriptGroupName());
        assertThat(new ObjectMapper().readTree(Path.of(repeated.scriptGroupFile()).toFile())
                .path("projects").size()).isEqualTo(5);
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

    private static long regularFileCount(Path root) throws Exception {
        if (!Files.isDirectory(root)) return 0;
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
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
                "沙脂蛹", 168, 4, 4, 164, "须弥", "selectLocalSpecialty_须弥");
        var gather = new CultivationExecutionProjection.GatherAction(
                "CD-Aware-AutoGather", "待执行",
                Map.of("runMode", "采集选中的材料",
                        "filterPathByKeywords", "晶蝶|水晶块|400精英",
                        "selectLocalSpecialty_须弥", List.of("沙脂蛹"),
                        "selectForgingOre", List.of("水晶块")),
                List.of(gatherTarget));
        var monsterTarget = new CultivationExecutionProjection.MonsterTarget(
                "织金红绸", 18, 0, 0, 18, "镀金旅团", List.of("镀金旅团·机弩兵"));
        var directRootMonsterTarget = new CultivationExecutionProjection.MonsterTarget(
                "寻宝鸦印", 36, 0, 0, 36, "盗宝团", List.of("盗宝团·斥候"));
        var mixedRootMonsterTarget = new CultivationExecutionProjection.MonsterTarget(
                "混合素材", 12, 0, 0, 12, "混合族", List.of("混合怪"));
        var monster = new CultivationExecutionProjection.MonsterAction(
                "FullyAutoAndSemiAutoTools", "待执行",
                Map.of("open_cd", true, "routeFamilies", List.of("镀金旅团", "盗宝团", "混合族"),
                        "treeLevel_0_0", List.of("锄地专区", "敌人与魔物"),
                        "treeLevel_1_0", List.of("精英400@汐"),
                        "treeLevel_1_1", List.of("巡陆艇"),
                        "config_white_list", "锄地专区"),
                List.of(monsterTarget, directRootMonsterTarget, mixedRootMonsterTarget),
                List.of("镀金旅团", "盗宝团", "混合族"));
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
