package com.cloud_guest.cultivation.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloud_guest.cultivation.CultivationUid;
import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfiguration;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.execution.module.CultivationModuleDefinition;
import com.cloud_guest.cultivation.execution.module.CultivationModuleSettingField;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.ScriptGroupSettingsExecutionModule;
import com.cloud_guest.cultivation.execution.module.WeeklyBossExecutionModule;
import com.cloud_guest.entitys.pojo.AutoPlanConfig;
import com.cloud_guest.service.AutoPlanService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CultivationOneStopService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Duration LAUNCH_REQUEST_TTL = Duration.ofMinutes(5);
    private final CultivationExecutionService executionService;
    private final CultivationModuleConfigurationService configurationService;
    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private final AutoPlanService autoPlanService;
    private final ObjectMapper objectMapper;

    @Value("${cultivation.bettergi-api-base-url:http://127.0.0.1:18081/bgi}")
    private String betterGiApiBaseUrl = "http://127.0.0.1:18081/bgi";

    public CultivationOneStopService(CultivationExecutionService executionService,
                                     CultivationModuleConfigurationService configurationService,
                                     CultivationMaterialSourceCatalog materialSourceCatalog,
                                     AutoPlanService autoPlanService,
                                     ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.configurationService = configurationService;
        this.materialSourceCatalog = materialSourceCatalog;
        this.autoPlanService = autoPlanService;
        this.objectMapper = objectMapper;
    }

    public List<CultivationModuleConfiguration> effectiveModules(String uid) {
        String normalizedUid = CultivationUid.normalize(uid);
        CultivationExecutionProjection projection = executionService.projection(normalizedUid);
        if (projection == null) return configurationService.findAll(normalizedUid);
        Path root = materialSourceCatalog.betterGiRoot();
        return configurationService.findAll(normalizedUid).stream().map(configuration -> {
            String moduleId = configuration.module().moduleId();
            ObjectNode effective = null;
            if (CdAwareAutoGatherExecutionModule.ID.equals(moduleId)) {
                effective = cultivationOnlyGatherSettings(root, projection.gatherAction(), new ArrayList<>());
            } else if (FullyAutoToolsExecutionModule.ID.equals(moduleId)) {
                effective = cultivationOnlyMonsterSettings(
                        root, normalizedUid, projection.monsterAction(), new ArrayList<>());
            }
            if (effective == null) return configuration;
            Map<String, Object> settings = objectMapper.convertValue(
                    effective, new TypeReference<LinkedHashMap<String, Object>>() {});
            return new CultivationModuleConfiguration(
                    effectiveDefinition(configuration.module(), effective),
                    configuration.enabled(), settings);
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationOneStopResult prepare(String uid) {
        String normalizedUid = CultivationUid.normalize(uid);
        CultivationExecutionProjection projection = executionService.projection(normalizedUid);
        if (projection == null) throw new IllegalStateException("该 UID 尚未建立养成账本");

        CultivationModuleConfiguration autoPlan = configurationService.find(normalizedUid, AutoPlanResinExecutionModule.ID);
        CultivationModuleConfiguration gather = configurationService.find(normalizedUid, CdAwareAutoGatherExecutionModule.ID);
        CultivationModuleConfiguration monster = configurationService.find(normalizedUid, FullyAutoToolsExecutionModule.ID);
        CultivationModuleConfiguration weekly = configurationService.find(normalizedUid, WeeklyBossExecutionModule.ID);
        CultivationModuleConfiguration groupSettings = configurationService.find(
                normalizedUid, ScriptGroupSettingsExecutionModule.ID);

        boolean hasPlanDrivenAction = hasPlanDrivenAction(projection, autoPlan);
        boolean hasInventoryReconcileTargets = hasInventoryReconcileTargets(projection);
        autoPlanService.remove(Wrappers.lambdaQuery(AutoPlanConfig.class)
                .eq(AutoPlanConfig::getUid, normalizedUid)
                .eq(AutoPlanConfig::getCultivate, Boolean.TRUE));

        Path root = materialSourceCatalog.betterGiRoot().toAbsolutePath().normalize();
        String groupName = "养成一条龙-" + projection.uid();
        Path scriptGroupRoot = root.resolve(Path.of("User", "ScriptGroup")).normalize();
        Path groupFile = scriptGroupRoot.resolve("养成一条龙-" + normalizedUid + ".json").normalize();
        if (!groupFile.startsWith(scriptGroupRoot)) {
            throw new IllegalArgumentException("UID 专属脚本组路径越出 BetterGI ScriptGroup 目录");
        }
        List<Path> duplicateGroupFiles = findManagedGroupDuplicates(root, normalizedUid, groupFile);
        List<String> warnings = new ArrayList<>();
        ObjectNode managedGroup = buildManagedGroup(
                root, groupFile, groupName, projection, autoPlan, gather, monster, weekly,
                groupSettings, warnings);

        Path backupDirectory = root.resolve(Path.of("User", "backup", "cultivation-one-stop",
                BACKUP_TIME.format(LocalDateTime.now())));
        try {
            for (Path duplicate : duplicateGroupFiles) {
                backup(duplicate, backupDirectory.resolve("ScriptGroup").resolve(duplicate.getFileName()));
            }
            if (hasPlanDrivenAction || hasInventoryReconcileTargets) {
                installPlanDrivenAutoPlanBridge(root, backupDirectory);
            }
            synchronizeScriptSettingsUi(root, normalizedUid, projection, backupDirectory, warnings);
            writeJsonIfChanged(
                    groupFile,
                    managedGroup,
                    backupDirectory.resolve("ScriptGroup").resolve(groupFile.getFileName()));
            for (Path duplicate : duplicateGroupFiles) Files.deleteIfExists(duplicate);
        } catch (IOException exception) {
            throw new IllegalStateException("无法生成 BetterGI 养成一条龙脚本组", exception);
        }

        int taskCount = managedGroup.path("projects").size();
        return new CultivationOneStopResult(
                normalizedUid, projection.revision(), groupName, groupFile.toString(), hasPlanDrivenAction ? 1 : 0, taskCount,
                backupDirectory.toString(), List.copyOf(warnings),
                "已生成 UID 专属计划驱动养成一条龙配置");
    }

    public CultivationLaunchResult start(String uid) {
        CultivationOneStopResult preparation = prepare(uid);
        String requestToken = UUID.randomUUID().toString();
        Path requestFile = materialSourceCatalog.betterGiRoot().resolve(Path.of(
                "User", "launch-requests", "cultivation-one-stop", requestToken + ".json"));
        Instant createdAt = Instant.now();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("version", 1);
        request.put("kind", "cultivation-one-stop");
        request.put("uid", preparation.uid());
        request.put("scriptGroupName", preparation.scriptGroupName());
        request.put("createdAtUtc", createdAt.toString());
        request.put("expiresAtUtc", createdAt.plus(LAUNCH_REQUEST_TTL).toString());
        try {
            writeJsonAtomically(requestFile, request);
            return new CultivationLaunchResult(
                    preparation,
                    "BetterGICultivation://one-stop?request=" + requestToken,
                    "配置已同步，正在交给 BetterGI 宿主启动专属养成脚本组");
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 BetterGI 宿主启动请求", exception);
        }
    }

    private static boolean hasPlanDrivenAction(CultivationExecutionProjection projection,
                                               CultivationModuleConfiguration configuration) {
        if (!configuration.enabled()) return false;
        return projection.resinActions().stream().anyMatch(action -> resinActionEnabled(action, configuration))
                || !projection.bossActions().isEmpty();
    }

    private static boolean hasInventoryReconcileTargets(CultivationExecutionProjection projection) {
        return !projection.gatherAction().csvTargets().isEmpty()
                || !projection.monsterAction().targets().isEmpty();
    }

    private ObjectNode buildManagedGroup(Path root,
                                         Path targetFile,
                                         String groupName,
                                         CultivationExecutionProjection projection,
                                         CultivationModuleConfiguration autoPlan,
                                         CultivationModuleConfiguration gather,
                                         CultivationModuleConfiguration monster,
                                         CultivationModuleConfiguration weekly,
                                         CultivationModuleConfiguration groupSettings,
                                         List<String> warnings) {
        List<GroupDocument> documents = readGroups(root, targetFile);
        ObjectNode template = documents.stream().findFirst()
                .map(document -> document.root().deepCopy())
                .orElseGet(objectMapper::createObjectNode);
        template.put("index", existingOrNextIndex(targetFile, documents));
        template.put("name", groupName);
        applyGroupSettings(root, template, groupSettings.settings());
        ArrayNode projects = objectMapper.createArrayNode();

        boolean hasEnabledResinAction = projection.resinActions().stream()
                .anyMatch(action -> resinActionEnabled(action, autoPlan));
        if (autoPlan.enabled() && (hasEnabledResinAction || !projection.bossActions().isEmpty())) {
            ObjectNode project = copyProject(documents, Set.of("AutoPlan"));
            if (project == null) throw new IllegalStateException("未找到已安装的 AutoPlan 脚本任务");
            ObjectNode settings = objectMapper.valueToTree(autoPlan.settings());
            settings.set("auto_load", mergedArray(settings.get("auto_load"), List.of("bgi_tools加载")));
            String autoPlanBase = betterGiApiUrl("/auto/plan");
            settings.put("bgi_tools_http_pull_json_config", autoPlanBase + "/json");
            settings.put("bgi_tools_http_push_all_json_config", autoPlanBase + "/domain/json/all");
            settings.put("bgi_tools_http_push_all_country_config", autoPlanBase + "/country/json/all");
            settings.put("bgi_tools_http_push_all_boss_config", autoPlanBase + "/boss/json/all");
            settings.put("cultivation_plan_mode", true);
            settings.put("run_config", "");
            settings.set("auto_check", objectMapper.createArrayNode());
            settings.put("bgi_tools_token", "");
            project.put("name", autoPlanTaskName(projection, autoPlan));
            prepareProject(project, projects.size() + 1, settings);
            projects.add(project);
        }
        if (gather.enabled() && !projection.gatherAction().csvTargets().isEmpty()) {
            ObjectNode project = copyProject(documents, Set.of("CD-Aware-AutoGather"));
            if (project == null) throw new IllegalStateException("未找到已安装的 CD-Aware-AutoGather 脚本任务");
            ObjectNode settings = cultivationOnlyGatherSettings(root, projection.gatherAction(), warnings);
            List<String> runnableMaterials = nonEmptyArraySettingSuffixes(settings, "selectRoute_");
            if (!runnableMaterials.isEmpty()) {
                project.put("name", taskName("养成采集", runnableMaterials));
                prepareProject(project, projects.size() + 1, settings);
                projects.add(project);
            }
        }
        if (monster.enabled() && !projection.monsterAction().targets().isEmpty()) {
            ObjectNode project = copyProject(documents,
                    Set.of("FullyAutoAndSemiAutoTools", "HCY-FullyAutoAndSemiAutoTools"));
            if (project == null) throw new IllegalStateException("未找到已安装的 FullyAutoAndSemiAutoTools 脚本任务");
            ObjectNode settings = cultivationOnlyMonsterSettings(
                    root, projection.uid(), projection.monsterAction(), warnings);
            List<String> families = stringList(settings.path("treeLevel_1_1"));
            if (!families.isEmpty()) {
                settings.remove("routeFamilies");
                project.put("name", taskName("养成怪物", families));
                prepareProject(project, projects.size() + 1, settings);
                projects.add(project);
            }
        }
        if (weekly.enabled() && !projection.weeklyBossActions().isEmpty()) {
            if (!Boolean.TRUE.equals(weekly.settings().get("unfairContractTerms"))) {
                warnings.add("周本脚本尚未确认风险条款，本次未加入专属脚本组");
            } else {
                for (CultivationExecutionProjection.WeeklyBossAction action : projection.weeklyBossActions()) {
                    ObjectNode project = newJavascriptProject(
                            "周本 - " + action.bossName(), "WeeklyBoss", projects.size() + 1,
                            objectMapper.valueToTree(action.settings()));
                    projects.add(project);
                }
            }
        }
        if (hasInventoryReconcileTargets(projection)) {
            ObjectNode project = copyProject(documents, Set.of("AutoPlan"));
            if (project == null) throw new IllegalStateException("未找到已安装的 AutoPlan 脚本任务");
            ObjectNode settings = objectMapper.valueToTree(autoPlan.settings());
            settings.set("auto_load", mergedArray(settings.get("auto_load"), List.of("bgi_tools加载")));
            String autoPlanBase = betterGiApiUrl("/auto/plan");
            settings.put("bgi_tools_http_pull_json_config", autoPlanBase + "/json");
            settings.put("bgi_tools_http_push_all_json_config", autoPlanBase + "/domain/json/all");
            settings.put("bgi_tools_http_push_all_country_config", autoPlanBase + "/country/json/all");
            settings.put("bgi_tools_http_push_all_boss_config", autoPlanBase + "/boss/json/all");
            settings.put("cultivation_plan_mode", false);
            settings.put("cultivation_inventory_reconcile_mode", true);
            settings.put("run_config", "");
            settings.set("auto_check", objectMapper.createArrayNode());
            settings.put("bgi_tools_token", "");
            project.put("name", "养成收尾：权威库存复核");
            prepareProject(project, projects.size() + 1, settings);
            projects.add(project);
        }
        if (projects.isEmpty()) warnings.add("当前没有可执行缺口，脚本组为空");
        template.set("projects", projects);
        return template;
    }

    private List<GroupDocument> readGroups(Path root, Path targetFile) {
        Path groupRoot = root.resolve(Path.of("User", "ScriptGroup"));
        try (var files = Files.list(groupRoot)) {
            List<GroupDocument> result = new ArrayList<>();
            for (Path file : files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing((Path path) -> !path.equals(targetFile))
                            .thenComparing(Path::toString))
                    .toList()) {
                JsonNode node = objectMapper.readTree(file.toFile());
                if (node instanceof ObjectNode object) result.add(new GroupDocument(file, object));
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 脚本组", exception);
        }
    }

    private ObjectNode copyProject(List<GroupDocument> documents, Set<String> aliases) {
        for (GroupDocument document : documents) {
            for (JsonNode project : document.root().path("projects")) {
                if (aliases.contains(project.path("folderName").asText()) && project instanceof ObjectNode object) {
                    return object.deepCopy();
                }
            }
        }
        return null;
    }

    private void prepareProject(ObjectNode project, int index, ObjectNode settings) {
        project.put("index", index);
        project.put("status", "Enabled");
        project.put("schedule", "Daily");
        project.put("runNum", 1);
        project.set("jsScriptSettingsObject", settings);
    }

    private ObjectNode newJavascriptProject(String name, String folderName, int index, ObjectNode settings) {
        ObjectNode project = objectMapper.createObjectNode();
        project.put("name", name);
        project.put("folderName", folderName);
        project.put("type", "Javascript");
        project.put("allowJsNotification", true);
        project.put("allowJsHTTPHash", "");
        prepareProject(project, index, settings);
        return project;
    }

    private ObjectNode cultivationOnlyGatherSettings(
            Path root,
            CultivationExecutionProjection.GatherAction action,
            List<String> warnings) {
        ObjectNode settings = objectMapper.valueToTree(action.settings());
        List<String> selectionKeys = new ArrayList<>();
        settings.fieldNames().forEachRemaining(key -> {
            if (key.startsWith("select")) selectionKeys.add(key);
        });
        selectionKeys.forEach(key -> settings.set(key, objectMapper.createArrayNode()));
        Map<String, List<String>> materialsBySelection = new LinkedHashMap<>();
        action.csvTargets().forEach(target -> {
            List<String> routes = gatherRouteOptions(root, target, true);
            if (routes.isEmpty()) {
                warnings.add("地方特产“" + target.materialName() + "”没有安全可用路线，本轮不生成该采集任务");
                return;
            }
            materialsBySelection.computeIfAbsent(target.selectionKey(), ignored -> new ArrayList<>())
                    .add(target.materialName());
            settings.set("selectRoute_" + target.materialName(), objectMapper.valueToTree(routes));
        });
        materialsBySelection.forEach((key, values) -> settings.set(key, objectMapper.valueToTree(values)));
        settings.put("runMode", "采集选中的材料");
        settings.put("targetCountOfSelected", "csv");
        settings.put("filterPathByKeywords", "");
        return settings;
    }

    private ObjectNode cultivationOnlyMonsterSettings(
            Path root,
            String uid,
            CultivationExecutionProjection.MonsterAction action,
            List<String> warnings) {
        ObjectNode settings = objectMapper.valueToTree(action.settings());
        List<String> families = stringList(settings.path("routeFamilies"));
        if (families.isEmpty()) {
            families = action.targets().stream()
                    .map(CultivationExecutionProjection.MonsterTarget::routeFamily)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct().toList();
        }
        MonsterRouteSelection routeSelection = concreteMonsterRouteSettings(root, uid, families);
        routeSelection.blockedFamilies().forEach(family -> warnings.add(
                "怪物材料路线“" + family + "”没有有效候选，本轮不生成该路线"));
        families = routeSelection.families();
        settings.set("routeFamilies", objectMapper.valueToTree(families));
        clearArraySettings(settings, "treeLevel_");
        settings.set("treeLevel_0_0", objectMapper.valueToTree(List.of("敌人与魔物")));
        settings.set("treeLevel_1_1", objectMapper.valueToTree(families));
        routeSelection.settings()
                .forEach((key, routes) -> settings.set(key, objectMapper.valueToTree(routes)));
        settings.put("config_white_list", "敌人与魔物");
        settings.put("high_level_filtering", "");
        settings.put("order_rules", "");
        settings.put("team_hoe_ground", filterFamilyRules(
                settings.path("team_hoe_ground").asText(""), families));
        settings.put("limit_max_group", filterFamilyRules(
                settings.path("limit_max_group").asText(""), families));
        settings.put("http_api", betterGiApiUrl("/cron/next-timestamp/all"));
        settings.put("cd_open", settings.path("open_cd").asBoolean(false));
        return settings;
    }

    private String betterGiApiUrl(String path) {
        String base = betterGiApiBaseUrl == null || betterGiApiBaseUrl.isBlank()
                ? "http://127.0.0.1:18081/bgi"
                : betterGiApiBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private MonsterRouteSelection concreteMonsterRouteSettings(
            Path root,
            String uid,
            Collection<String> families) {
        for (String alias : List.of("HCY-FullyAutoAndSemiAutoTools", "FullyAutoAndSemiAutoTools")) {
            Path uidSettings = root.resolve(Path.of(
                    "User", "JsScript", alias, "config", "uidSettings.json"));
            if (!Files.isRegularFile(uidSettings)) continue;
            try {
                JsonNode entries = objectMapper.readTree(uidSettings.toFile());
                for (JsonNode entry : entries) {
                    if (!entry.isArray() || entry.size() < 2 || !uid.equals(entry.get(0).asText())) continue;
                    Map<String, String> fieldByFamily = new LinkedHashMap<>();
                    Map<String, List<CultivationMonsterRouteSelector.Candidate>> candidatesByFamily =
                            new LinkedHashMap<>();
                    for (String family : families) {
                        String marker = "->[" + family + "]";
                        for (JsonNode field : entry.get(1)) {
                            String name = field.path("name").asText();
                            if (!name.startsWith("treeLevel_2_")
                                    || !field.path("label").asText().contains(marker)
                                    || !field.path("options").isArray()) continue;
                            LinkedHashSet<String> options = new LinkedHashSet<>(stringList(field.path("options")));
                            options.addAll(monsterRouteBundleDirectories(root, family));
                            if (options.isEmpty()) continue;
                            fieldByFamily.put(family, name);
                            candidatesByFamily.put(family, options.stream()
                                    .map(option -> inspectMonsterRouteBundle(root, family, option))
                                    .toList());
                        }
                    }
                    Map<String, String> selected = CultivationMonsterRouteSelector.select(candidatesByFamily);
                    Map<String, List<String>> result = new LinkedHashMap<>();
                    selected.forEach((family, option) -> {
                        String field = fieldByFamily.get(family);
                        if (field != null) result.put(field, List.of(option));
                    });
                    List<String> selectedFamilies = families.stream().filter(selected::containsKey).toList();
                    List<String> blockedFamilies = families.stream().filter(family -> !selected.containsKey(family)).toList();
                    return new MonsterRouteSelection(result, selectedFamilies, blockedFamilies);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("无法读取 FullyAutoAndSemiAutoTools UID 路线设置", exception);
            }
        }
        return new MonsterRouteSelection(Map.of(), List.of(), List.copyOf(families));
    }

    private List<String> monsterRouteBundleDirectories(Path root, String family) throws IOException {
        Path familyRoot = root.resolve(Path.of("User", "AutoPathing", "敌人与魔物", family));
        if (!Files.isDirectory(familyRoot)) return List.of();
        try (var children = Files.list(familyRoot)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.matches(".*(低成功率|低效|不跑|不刷|不稳定|不可用|暂不可用).*"))
                    .sorted(String::compareTo)
                    .toList();
        }
    }

    private CultivationMonsterRouteSelector.Candidate inspectMonsterRouteBundle(
            Path root,
            String family,
            String option) {
        Path bundleRoot = root.resolve(Path.of("User", "AutoPathing", "敌人与魔物", family, option));
        if (!Files.isDirectory(bundleRoot)
                || option.matches(".*(低成功率|低效|不跑|不刷|不稳定|不可用|暂不可用).*")) {
            return new CultivationMonsterRouteSelector.Candidate(option, 0, 0, 0, false);
        }

        int routeCount = 0;
        int fightActions = 0;
        boolean valid = true;
        try (var paths = Files.walk(bundleRoot)) {
            List<Path> routeFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            routeCount = routeFiles.size();
            for (Path routeFile : routeFiles) {
                JsonNode route = objectMapper.readTree(routeFile.toFile());
                JsonNode positions = route.path("positions");
                if (!positions.isArray()) {
                    valid = false;
                    continue;
                }
                for (JsonNode position : positions) {
                    if ("fight".equalsIgnoreCase(position.path("action").asText())) fightActions++;
                }
            }
        } catch (IOException | RuntimeException exception) {
            valid = false;
        }
        if (routeCount == 0 || fightActions == 0) valid = false;
        return new CultivationMonsterRouteSelector.Candidate(
                option,
                routeCount,
                fightActions,
                historicalMonsterRouteFailures(root, family, option),
                valid);
    }

    private int historicalMonsterRouteFailures(Path root, String family, String option) {
        String marker = ("\\" + family + "\\" + option + "\\").toLowerCase(java.util.Locale.ROOT);
        for (String alias : List.of("HCY-FullyAutoAndSemiAutoTools", "FullyAutoAndSemiAutoTools")) {
            Path recordFile = root.resolve(Path.of("User", "JsScript", alias, "config", "record.json"));
            if (!Files.isRegularFile(recordFile)) continue;
            try {
                int failures = 0;
                for (JsonNode record : objectMapper.readTree(recordFile.toFile())) {
                    for (JsonNode errorPath : record.path("errorPaths")) {
                        String normalized = errorPath.asText().replace('/', '\\')
                                .toLowerCase(java.util.Locale.ROOT);
                        if (normalized.contains(marker)) failures++;
                    }
                }
                return failures;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "无法读取怪物路线失败记录，拒绝把未知历史当作零失败：" + recordFile, exception);
            }
        }
        return 0;
    }

    private List<String> gatherRouteOptions(
            Path root,
            CultivationExecutionProjection.GatherTarget target,
            boolean safeOnly) {
        Path materialRoot = root.resolve(Path.of("User", "AutoPathing", "地方特产",
                target.country(), target.materialName()));
        if (!Files.isDirectory(materialRoot)) return List.of();
        try (var paths = Files.walk(materialRoot)) {
            List<String> routes = paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".json"))
                    .map(Path::getParent)
                    .map(materialRoot::relativize)
                    .map(relative -> relative.getNameCount() == 0
                            ? "(根目录)" : relative.getName(0).toString())
                    .distinct()
                    .sorted(String::compareTo)
                    .toList();
            if (!safeOnly) return routes;
            return safeGatherRouteNames(routes);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取地方特产路线：" + target.materialName(), exception);
        }
    }

    private CultivationModuleDefinition effectiveDefinition(
            CultivationModuleDefinition definition,
            ObjectNode settings) {
        List<CultivationModuleSettingField> fields = new ArrayList<>(definition.settingsSchema());
        Set<String> knownKeys = fields.stream()
                .map(CultivationModuleSettingField::key)
                .collect(java.util.stream.Collectors.toSet());
        settings.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (knownKeys.contains(key) || !entry.getValue().isArray() || entry.getValue().isEmpty()) return;
            boolean managedGather = key.startsWith("selectLocalSpecialty_") || key.startsWith("selectRoute_");
            boolean managedMonster = key.startsWith("treeLevel_");
            if (!managedGather && !managedMonster) return;
            String label = effectiveFieldLabel(key);
            fields.add(new CultivationModuleSettingField(
                    key, label, "multi-select", false, null, stringList(entry.getValue())));
        });
        return new CultivationModuleDefinition(
                definition.moduleId(), definition.displayName(), definition.adapterVersion(),
                definition.description(), definition.integrationState(), definition.capabilities(),
                List.copyOf(fields));
    }

    private static String effectiveFieldLabel(String key) {
        if (key.startsWith("selectLocalSpecialty_")) {
            return key.substring("selectLocalSpecialty_".length()) + "地方特产";
        }
        if (key.startsWith("selectRoute_")) {
            return key.substring("selectRoute_".length()) + "具体路线";
        }
        if ("treeLevel_0_0".equals(key)) return "一级路线目录";
        if ("treeLevel_1_1".equals(key)) return "账本怪物路线分类";
        return "具体怪物路线（" + key + "）";
    }

    private void synchronizeScriptSettingsUi(
            Path root,
            String uid,
            CultivationExecutionProjection projection,
            Path backupDirectory,
            List<String> warnings) throws IOException {
        synchronizeGatherSettingsUi(root, projection.gatherAction(), backupDirectory, warnings);
        synchronizeMonsterSettingsUi(root, uid, backupDirectory, warnings);
    }

    private void synchronizeGatherSettingsUi(
            Path root,
            CultivationExecutionProjection.GatherAction action,
            Path backupDirectory,
            List<String> warnings) throws IOException {
        Path scriptRoot = root.resolve(Path.of("User", "JsScript", "CD-Aware-AutoGather"));
        Path settingsFile = scriptRoot.resolve("settings.json");
        if (!Files.isRegularFile(settingsFile)) {
            warnings.add("未找到 CD-Aware-AutoGather 动态设置表，BetterGI 配置页可能暂不显示材料路线");
            return;
        }
        JsonNode current = objectMapper.readTree(settingsFile.toFile());
        if (!(current instanceof ArrayNode settingsUi)) {
            throw new IllegalStateException("CD-Aware-AutoGather settings.json 格式无效");
        }
        for (CultivationExecutionProjection.GatherTarget target : action.csvTargets()) {
            Path countryRoot = root.resolve(Path.of(
                    "User", "AutoPathing", "地方特产", target.country()));
            List<String> materials = directoryNames(countryRoot);
            if (!materials.contains(target.materialName())) {
                materials = new ArrayList<>(materials);
                materials.add(target.materialName());
                materials = materials.stream().distinct().sorted(String::compareTo).toList();
            }
            upsertSettingsField(settingsUi, selectionField(
                    target.selectionKey(), target.country() + "地方特产", materials));
            List<String> allRoutes = gatherRouteOptions(root, target, false);
            if (!allRoutes.isEmpty()) {
                upsertSettingsField(settingsUi, selectionField(
                        "selectRoute_" + target.materialName(),
                        target.materialName() + "具体路线", allRoutes));
            }
        }
        writeJsonIfChanged(
                settingsFile,
                settingsUi,
                backupDirectory.resolve(Path.of(
                        "ScriptSettings", "CD-Aware-AutoGather", "settings.json")));
    }

    private void synchronizeMonsterSettingsUi(
            Path root,
            String uid,
            Path backupDirectory,
            List<String> warnings) throws IOException {
        for (String alias : List.of("HCY-FullyAutoAndSemiAutoTools", "FullyAutoAndSemiAutoTools")) {
            Path scriptRoot = root.resolve(Path.of("User", "JsScript", alias));
            Path uidSettingsFile = scriptRoot.resolve(Path.of("config", "uidSettings.json"));
            Path settingsFile = scriptRoot.resolve("settings.json");
            if (!Files.isRegularFile(uidSettingsFile) || !Files.isRegularFile(settingsFile)) continue;
            ArrayNode definitions = uidSettingsDefinitions(uidSettingsFile, uid);
            if (definitions == null) continue;
            writeJsonIfChanged(
                    settingsFile,
                    definitions,
                    backupDirectory.resolve(Path.of(
                            "ScriptSettings", alias, "settings.json")));
            return;
        }
        warnings.add("未找到 FullyAutoAndSemiAutoTools 当前 UID 动态设置表，BetterGI 配置页可能暂不显示怪物路线");
    }

    private ArrayNode uidSettingsDefinitions(Path uidSettingsFile, String uid) throws IOException {
        JsonNode entries = objectMapper.readTree(uidSettingsFile.toFile());
        for (JsonNode entry : entries) {
            if (entry.isArray() && entry.size() >= 2 && uid.equals(entry.get(0).asText())
                    && entry.get(1) instanceof ArrayNode definitions) {
                return definitions.deepCopy();
            }
        }
        return null;
    }

    private ObjectNode selectionField(String name, String label, List<String> options) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("name", name);
        field.put("type", "multi-checkbox");
        field.put("label", label);
        field.set("options", objectMapper.valueToTree(options));
        return field;
    }

    private static void upsertSettingsField(ArrayNode settingsUi, ObjectNode replacement) {
        String name = replacement.path("name").asText();
        for (int index = 0; index < settingsUi.size(); index++) {
            if (name.equals(settingsUi.get(index).path("name").asText())) {
                settingsUi.set(index, replacement);
                return;
            }
        }
        settingsUi.add(replacement);
    }

    private static List<String> directoryNames(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(String::compareTo)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取路线材料目录：" + root, exception);
        }
    }

    private void applyGroupSettings(Path root, ObjectNode group, Map<String, Object> settings) {
        ObjectNode config = childObject(group, "config");
        ObjectNode pathing = childObject(config, "pathingConfig");
        pathing.put("enabled", booleanSetting(settings, "pathingEnabled", true));
        pathing.put("partyName", stringSetting(settings, "partyName", ""));
        pathing.put("autoPickEnabled", booleanSetting(settings, "autoPickEnabled", true));
        pathing.put("mainAvatarIndex", stringSetting(settings, "mainAvatarIndex", "1"));
        pathing.put("guardianAvatarIndex", stringSetting(settings, "guardianAvatarIndex", "1"));
        pathing.put("guardianElementalSkillSecondInterval",
                stringSetting(settings, "guardianSkillIntervalSeconds", ""));
        pathing.put("guardianElementalSkillLongPress",
                booleanSetting(settings, "guardianSkillLongPress", true));
        pathing.put("isVisitStatueBeforeSwitchParty",
                booleanSetting(settings, "visitStatueBeforeSwitchParty", false));
        pathing.put("onlyInTeleportRecover", booleanSetting(settings, "onlyInTeleportRecover", false));
        pathing.put("jsScriptUseEnabled", booleanSetting(settings, "jsScriptUseEnabled", true));
        pathing.put("soloTaskUseFightEnabled", booleanSetting(settings, "soloTaskUseFightEnabled", true));
        pathing.put("skipDuring", stringSetting(settings, "skipDuring", ""));
        pathing.put("useGadgetIntervalMs", intSetting(settings, "useGadgetIntervalMs", 0));
        pathing.put("autoSkipEnabled", booleanSetting(settings, "autoSkipEnabled", true));
        pathing.put("autoRunEnabled", booleanSetting(settings, "autoRunEnabled", true));
        pathing.put("autoEatEnabled", booleanSetting(settings, "autoEatEnabled", false));
        pathing.put("hideOnRepeat", booleanSetting(settings, "hideOnRepeat", false));
        pathing.put("distance", intSetting(settings, "distance", 45));
        pathing.put("approachStopDistance", intSetting(settings, "approachStopDistance", 25));
        pathing.put("hurryOnAvatar", stringSetting(settings, "hurryOnAvatar", ""));
        pathing.put("travelMode", stringSetting(settings, "travelMode", "精准靠近"));
        pathing.put("switchToWalkEnabled", booleanSetting(settings, "switchToWalkEnabled", false));
        pathing.put("autoFightEnabled", booleanSetting(settings, "autoFightEnabled", true));

        ObjectNode fight = childObject(pathing, "autoFightConfig");
        fight.put("strategyName", stringSetting(settings, "autoFightStrategyName", "根据队伍自动选择"));
        fight.put("teamNames", stringSetting(settings, "autoFightTeamNames", ""));
        fight.put("burstEnabled", betterGiBurstEnabled(root, fight));
        fight.put("fightFinishDetectEnabled", booleanSetting(settings, "fightFinishDetectEnabled", true));
        fight.put("pickDropsAfterFightEnabled", booleanSetting(settings, "pickDropsAfterFightEnabled", true));
        fight.put("pickDropsAfterFightSeconds", intSetting(settings, "pickDropsAfterFightSeconds", 60));
        fight.put("timeout", intSetting(settings, "fightTimeoutSeconds", 200));

        ObjectNode cycle = childObject(pathing, "taskCycleConfig");
        cycle.put("enable", booleanSetting(settings, "taskCycleEnabled", false));
        cycle.put("boundaryTime", intSetting(settings, "taskCycleBoundaryTime", 0));
        cycle.put("isBoundaryTimeBasedOnServerTime",
                booleanSetting(settings, "taskCycleServerTime", false));
        cycle.put("cycle", intSetting(settings, "taskCycleDays", 1));
        if (!cycle.has("index")) cycle.put("index", 1);

        ObjectNode completion = childObject(pathing, "taskCompletionSkipRuleConfig");
        completion.put("enable", booleanSetting(settings, "completionSkipEnabled", false));
        completion.put("skipPolicy", "GroupPhysicalPathSkipPolicy");
        completion.put("boundaryTime", 0);
        completion.put("isBoundaryTimeBasedOnServerTime", false);
        completion.put("lastRunGapSeconds",
                intSetting(settings, "completionSkipLastRunGapSeconds", -1));
        completion.put("referencePoint", "EndTime");

        ObjectNode priority = childObject(pathing, "preExecutionPriorityConfig");
        priority.put("enabled", booleanSetting(settings, "preExecutionPriorityEnabled", false));
        priority.put("groupNames", stringSetting(settings, "preExecutionPriorityGroupNames", ""));
        priority.put("maxRetryCount", intSetting(settings, "preExecutionPriorityMaxRetryCount", 1));

        config.put("enableShellConfig", booleanSetting(settings, "shellEnabled", false));
        ObjectNode shell = childObject(config, "shellConfig");
        shell.put("disable", booleanSetting(settings, "shellDisabled", false));
        shell.put("timeout", intSetting(settings, "shellTimeoutSeconds", 60));
        shell.put("noWindow", booleanSetting(settings, "shellNoWindow", true));
        shell.put("output", booleanSetting(settings, "shellOutput", true));
    }

    private ObjectNode childObject(ObjectNode parent, String key) {
        JsonNode current = parent.get(key);
        if (current instanceof ObjectNode object) return object;
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(key, created);
        return created;
    }

    private void clearArraySettings(ObjectNode settings, String prefix) {
        List<String> keys = new ArrayList<>();
        settings.fieldNames().forEachRemaining(key -> {
            if (key.startsWith(prefix)) keys.add(key);
        });
        keys.forEach(key -> settings.set(key, objectMapper.createArrayNode()));
    }

    private static String filterFamilyRules(String raw, Collection<String> families) {
        if (raw == null || raw.isBlank()) return "";
        Set<String> prefixes = families.stream()
                .map(family -> "敌人与魔物->" + family + "=")
                .collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(rule -> prefixes.stream().anyMatch(rule::startsWith))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String autoPlanTaskName(CultivationExecutionProjection projection,
                                           CultivationModuleConfiguration configuration) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        projection.resinActions().stream()
                .filter(action -> resinActionEnabled(action, configuration))
                .map(CultivationOneStopService::resinActionLabel)
                .forEach(labels::add);
        if (!projection.bossActions().isEmpty()) labels.add("世界首领");
        return taskName("养成体力", labels);
    }

    private static String resinActionLabel(CultivationExecutionProjection.ResinAction action) {
        if ("天赋".equals(action.sourceType())) return "天赋书";
        if ("藏金之花".equals(action.sourceName()) || "摩拉".equals(action.materialName())) return "摩拉";
        if ("启示之花".equals(action.sourceName()) || "大英雄的经验".equals(action.materialName())) {
            return "大英雄经验";
        }
        return action.sourceName();
    }

    private static String taskName(String prefix, Collection<String> contents) {
        List<String> values = contents.stream().filter(value -> value != null && !value.isBlank())
                .distinct().toList();
        if (values.isEmpty()) return prefix;
        if (values.size() <= 4) return prefix + "：" + String.join("·", values);
        return prefix + "：" + String.join("·", values.subList(0, 3)) + "等" + values.size() + "项";
    }

    private static boolean resinActionEnabled(CultivationExecutionProjection.ResinAction action,
                                              CultivationModuleConfiguration configuration) {
        if ("天赋".equals(action.sourceType())) {
            return booleanSetting(configuration, "talentDomainEnabled", true);
        }
        if ("藏金之花".equals(action.sourceName()) || "摩拉".equals(action.materialName())) {
            return booleanSetting(configuration, "moraLeyLineEnabled", true);
        }
        if ("启示之花".equals(action.sourceName()) || "大英雄的经验".equals(action.materialName())) {
            return booleanSetting(configuration, "experienceLeyLineEnabled", true);
        }
        return true;
    }

    private List<Path> findManagedGroupDuplicates(Path root, String uid, Path canonicalFile) {
        Path groupRoot = root.resolve(Path.of("User", "ScriptGroup"));
        String canonicalName = "养成一条龙-" + uid;
        try (var files = Files.list(groupRoot)) {
            List<Path> duplicates = new ArrayList<>();
            for (Path file : files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".json")
                            && !path.equals(canonicalFile))
                    .toList()) {
                String stem = file.getFileName().toString();
                stem = stem.substring(0, stem.length() - ".json".length());
                boolean ownedByFileName = stem.equals(canonicalName) || stem.startsWith(canonicalName + "-");
                boolean ownedByGroupName = false;
                try {
                    String groupName = objectMapper.readTree(file.toFile()).path("name").asText();
                    ownedByGroupName = groupName.equals(canonicalName)
                            || groupName.startsWith(canonicalName + "-");
                } catch (IOException ignored) {
                    // A matching generated filename still belongs to this UID even if its JSON is damaged.
                }
                if (ownedByFileName || ownedByGroupName) duplicates.add(file);
            }
            return List.copyOf(duplicates);
        } catch (IOException exception) {
            throw new IllegalStateException("无法检查 BetterGI 养成配置组唯一性", exception);
        }
    }

    private ArrayNode mergedArray(JsonNode current, Collection<String> incoming) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (current != null && current.isArray()) current.forEach(item -> values.add(item.asText()));
        values.addAll(incoming);
        return objectMapper.valueToTree(values);
    }

    private int existingOrNextIndex(Path targetFile, List<GroupDocument> documents) {
        if (Files.isRegularFile(targetFile)) {
            try {
                return objectMapper.readTree(targetFile.toFile()).path("index").asInt(9999);
            } catch (IOException ignored) {
                return 9999;
            }
        }
        return documents.stream().map(document -> document.root().path("index").asInt(0))
                .max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    private static void backup(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean betterGiBurstEnabled(Path root, ObjectNode existingFightSettings) {
        Path configFile = root.resolve(Path.of("User", "config.json"));
        if (!Files.isRegularFile(configFile)) {
            return existingFightSettings.path("burstEnabled").asBoolean(false);
        }
        try {
            JsonNode configured = objectMapper.readTree(configFile.toFile())
                    .path("autoFightConfig").path("burstEnabled");
            return configured.isBoolean()
                    ? configured.asBoolean()
                    : existingFightSettings.path("burstEnabled").asBoolean(false);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 现有盾位元素爆发开关", exception);
        }
    }

    private void installPlanDrivenAutoPlanBridge(Path root, Path backupDirectory) throws IOException {
        Path scriptRoot = root.resolve(Path.of("User", "JsScript", "AutoPlan"));
        Path mainFile = scriptRoot.resolve("main.js");
        if (!Files.isRegularFile(mainFile)) {
            throw new IllegalStateException("未找到 AutoPlan/main.js，无法启用计划驱动执行");
        }

        String bridge;
        try (InputStream input = getClass().getResourceAsStream(
                "/cultivation/autoplan/cultivation_plan.js")) {
            if (input == null) throw new IllegalStateException("计划驱动 AutoPlan 桥接资源缺失");
            bridge = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Path bridgeFile = scriptRoot.resolve(Path.of("utils", "cultivation_plan.js"));
        writeTextIfChanged(
                bridgeFile, bridge,
                backupDirectory.resolve(Path.of("JsScript", "AutoPlan", "utils", "cultivation_plan.js")));
        installAutoPlanRewardPassthrough(scriptRoot, backupDirectory);

        String main = Files.readString(mainFile);
        String oldImportLine = "import {runPlanDrivenCultivation} from './utils/cultivation_plan';";
        String importLine = "import {runPlanDrivenCultivation, runCultivationInventoryReconcile} "
                + "from './utils/cultivation_plan';";
        if (main.contains(oldImportLine)) {
            main = main.replace(oldImportLine, importLine);
        } else if (!main.contains(importLine)) {
            main = importLine + System.lineSeparator() + main;
        }
        if (!main.contains("settings.cultivation_inventory_reconcile_mode")) {
            String anchor = "    await init();";
            int mainStart = main.indexOf("async function main()");
            int anchorIndex = main.indexOf(anchor, mainStart);
            if (mainStart < 0 || anchorIndex < 0) {
                throw new IllegalStateException("AutoPlan/main.js 结构已变化，无法安全安装库存复核桥接");
            }
            String reconcileMode = anchor + System.lineSeparator()
                    + "    if (settings.cultivation_inventory_reconcile_mode) {" + System.lineSeparator()
                    + "        await runCultivationInventoryReconcile(config);" + System.lineSeparator()
                    + "        return;" + System.lineSeparator()
                    + "    }";
            main = main.substring(0, anchorIndex) + reconcileMode
                    + main.substring(anchorIndex + anchor.length());
        }
        if (!main.contains("settings.cultivation_plan_mode")) {
            String anchor = "    await init();";
            int mainStart = main.indexOf("async function main()");
            int anchorIndex = main.indexOf(anchor, mainStart);
            if (mainStart < 0 || anchorIndex < 0) {
                throw new IllegalStateException("AutoPlan/main.js 结构已变化，无法安全安装计划驱动桥接");
            }
            String planMode = anchor + System.lineSeparator()
                    + "    if (settings.cultivation_plan_mode) {" + System.lineSeparator()
                    + "        await runPlanDrivenCultivation(config);" + System.lineSeparator()
                    + "        return;" + System.lineSeparator()
                    + "    }";
            main = main.substring(0, anchorIndex) + planMode + main.substring(anchorIndex + anchor.length());
        }
        writeTextIfChanged(
                mainFile, main,
                backupDirectory.resolve(Path.of("JsScript", "AutoPlan", "main.js")));
    }

    private void installAutoPlanRewardPassthrough(Path scriptRoot, Path backupDirectory) throws IOException {
        Path handlerFile = scriptRoot.resolve(Path.of("utils", "load_check_run.js"));
        if (!Files.isRegularFile(handlerFile)) {
            throw new IllegalStateException("未找到 AutoPlan/utils/load_check_run.js，无法回传实际奖励");
        }
        String source = Files.readString(handlerFile);
        source = ensureReturnedDispatcherCall(source, "dispatcher.RunAutoDomainTask(domainParam)");
        source = ensureReturnedDispatcherCall(source, "dispatcher.RunAutoBossTask(param)");
        writeTextIfChanged(
                handlerFile, source,
                backupDirectory.resolve(Path.of("JsScript", "AutoPlan", "utils", "load_check_run.js")));
    }

    private static String ensureReturnedDispatcherCall(String source, String call) {
        String returned = "return await " + call + ";";
        if (source.contains(returned)) return source;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?m)^(\\h*)await " + java.util.regex.Pattern.quote(call) + ";?\\h*$");
        java.util.regex.Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("AutoPlan 奖励回传锚点已变化：" + call);
        }
        return matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(matcher.group(1) + returned));
    }

    private static void writeTextIfChanged(Path target, String value, Path backupTarget) throws IOException {
        if (Files.isRegularFile(target)) {
            String current = Files.readString(target);
            if (current.equals(value)) return;
            backup(target, backupTarget);
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, value);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeJsonIfChanged(Path target, JsonNode value, Path backupTarget) throws IOException {
        if (Files.isRegularFile(target)) {
            JsonNode current = objectMapper.readTree(target.toFile());
            if (current.equals(value)) return;
            backup(target, backupTarget);
        }
        writeJsonAtomically(target, value);
    }

    private void writeJsonAtomically(Path target, JsonNode value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean booleanSetting(CultivationModuleConfiguration configuration,
                                          String key,
                                          boolean fallback) {
        Object value = configuration.settings().get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringSetting(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int intSetting(Map<String, Object> settings, String key, int fallback) {
        Object value = settings.get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean booleanSetting(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> stringList(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asText()));
        return result;
    }

    private static List<String> nonEmptyArraySettingSuffixes(ObjectNode settings, String prefix) {
        List<String> result = new ArrayList<>();
        settings.fields().forEachRemaining(entry -> {
            if (entry.getKey().startsWith(prefix)
                    && entry.getValue().isArray()
                    && !entry.getValue().isEmpty()) {
                result.add(entry.getKey().substring(prefix.length()));
            }
        });
        return List.copyOf(result);
    }

    static List<String> safeGatherRouteNames(Collection<String> routes) {
        return routes.stream()
                .filter(route -> route != null
                        && !route.matches(".*(低成功率|低效|不跑|不刷|不稳定|不可用|暂不可用).*"))
                .toList();
    }

    private record MonsterRouteSelection(
            Map<String, List<String>> settings,
            List<String> families,
            List<String> blockedFamilies) {
    }

    private record GroupDocument(Path path, ObjectNode root) {
    }
}
