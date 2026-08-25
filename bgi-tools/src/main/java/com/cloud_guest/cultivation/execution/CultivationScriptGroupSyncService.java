package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CultivationScriptGroupSyncService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Set<String> CD_AWARE_ALIASES = Set.of("CD-Aware-AutoGather");
    private static final Set<String> FULLY_AUTO_ALIASES = Set.of(
            "FullyAutoAndSemiAutoTools", "HCY-FullyAutoAndSemiAutoTools");

    private final CultivationExecutionService executionService;
    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private final ObjectMapper objectMapper;

    public CultivationScriptGroupSyncService(CultivationExecutionService executionService,
                                             CultivationMaterialSourceCatalog materialSourceCatalog,
                                             ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.materialSourceCatalog = materialSourceCatalog;
        this.objectMapper = objectMapper;
    }

    public CultivationScriptSyncResult sync(String uid, String moduleId) {
        if (!CdAwareAutoGatherExecutionModule.ID.equals(moduleId)
                && !FullyAutoToolsExecutionModule.ID.equals(moduleId)) {
            throw new IllegalArgumentException("该模块不需要同步 BetterGI 脚本组：" + moduleId);
        }
        CultivationExecutionProjection projection = executionService.projection(uid);
        if (projection == null) throw new IllegalStateException("该 UID 尚未建立养成账本");
        String normalizedUid = projection.uid();

        Path root = materialSourceCatalog.betterGiRoot().toAbsolutePath().normalize();
        Path scriptGroupRoot = root.resolve(Path.of("User", "ScriptGroup")).normalize();
        if (!Files.isDirectory(scriptGroupRoot)) {
            throw new IllegalStateException("未找到 BetterGI 脚本组目录：" + scriptGroupRoot);
        }
        Path cultivationGroupFile = scriptGroupRoot.resolve("养成一条龙-" + normalizedUid + ".json").normalize();
        if (!cultivationGroupFile.startsWith(scriptGroupRoot)) {
            throw new IllegalArgumentException("UID 专属脚本组路径越出 BetterGI ScriptGroup 目录");
        }
        if (!Files.isRegularFile(cultivationGroupFile)) {
            throw new IllegalStateException("请先生成 UID 专属养成一条龙配置：" + cultivationGroupFile);
        }

        boolean cdAware = CdAwareAutoGatherExecutionModule.ID.equals(moduleId);
        Set<String> aliases = cdAware ? CD_AWARE_ALIASES : FULLY_AUTO_ALIASES;
        Map<String, Object> settings = cdAware
                ? projection.gatherAction().settings()
                : projection.monsterAction().settings();
        Set<String> generatedSelectionKeys = new LinkedHashSet<>();
        if (cdAware) {
            projection.gatherAction().csvTargets().stream()
                    .map(CultivationExecutionProjection.GatherTarget::selectionKey)
                    .forEach(generatedSelectionKeys::add);
        }

        Path backupRoot = root.resolve(Path.of("User", "backup", "cultivation-module-sync",
                BACKUP_TIME.format(LocalDateTime.now())));
        List<String> changedFiles = new ArrayList<>();
        int updatedTasks;
        try {
            JsonNode rootNode = objectMapper.readTree(cultivationGroupFile.toFile());
            updatedTasks = updateTasks(rootNode, aliases, settings, generatedSelectionKeys, cdAware);
            if (updatedTasks == 0) {
                throw new IllegalStateException("UID 专属配置中未找到对应 BetterGI 脚本任务："
                        + String.join(" / ", aliases));
            }
            backup(cultivationGroupFile,
                    backupRoot.resolve("ScriptGroup").resolve(cultivationGroupFile.getFileName()));
            writeJsonAtomically(cultivationGroupFile, rootNode);
            changedFiles.add(cultivationGroupFile.getFileName().toString());
            if (cdAware) {
                syncGatherTargets(root, normalizedUid, projection.gatherAction().csvTargets(), backupRoot);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("同步 BetterGI 脚本组失败", exception);
        }

        return new CultivationScriptSyncResult(
                moduleId, updatedTasks, List.copyOf(changedFiles), backupRoot.toString(),
                cdAware
                        ? "已同步 CD-Aware-AutoGather 设置与材料目标"
                        : "已同步 FullyAutoAndSemiAutoTools 设置与怪物路线");
    }

    private int updateTasks(JsonNode node,
                            Set<String> aliases,
                            Map<String, Object> settings,
                            Set<String> generatedSelectionKeys,
                            boolean cdAware) {
        int changed = 0;
        if (node instanceof ObjectNode object) {
            if (aliases.contains(object.path("folderName").asText())
                    && object.path("jsScriptSettingsObject") instanceof ObjectNode scriptSettings) {
                mergeSettings(scriptSettings, settings, generatedSelectionKeys, cdAware);
                changed++;
            }
            var fields = object.fields();
            while (fields.hasNext()) changed += updateTasks(
                    fields.next().getValue(), aliases, settings, generatedSelectionKeys, cdAware);
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) {
                changed += updateTasks(child, aliases, settings, generatedSelectionKeys, cdAware);
            }
        }
        return changed;
    }

    private void mergeSettings(ObjectNode target,
                               Map<String, Object> settings,
                               Set<String> generatedSelectionKeys,
                               boolean cdAware) {
        settings.forEach((key, value) -> {
            if ("routeFamilies".equals(key)) return;
            if (cdAware && generatedSelectionKeys.contains(key)) {
                target.set(key, mergedArray(target.get(key), value));
            } else {
                target.set(key, objectMapper.valueToTree(value));
            }
        });
        if (!cdAware) {
            List<String> routeFamilies = stringList(settings.get("routeFamilies"));
            target.set("treeLevel_0_0", mergedArray(target.get("treeLevel_0_0"), List.of("敌人与魔物")));
            target.set("treeLevel_1_1", mergedArray(target.get("treeLevel_1_1"), routeFamilies));
            target.put("cd_open", Boolean.TRUE.equals(settings.get("open_cd")));
        }
    }

    private ArrayNode mergedArray(JsonNode current, Object incoming) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (current != null && current.isArray()) current.forEach(item -> values.add(item.asText()));
        values.addAll(stringList(incoming));
        return objectMapper.valueToTree(values);
    }

    private void syncGatherTargets(Path root,
                                   String uid,
                                   List<CultivationExecutionProjection.GatherTarget> targets,
                                   Path backupRoot) throws IOException {
        Path csv = root.resolve(Path.of("User", "JsScript", "CD-Aware-AutoGather",
                "record", uid, "采集目标.csv"));
        Map<String, String> rows = new LinkedHashMap<>();
        if (Files.isRegularFile(csv)) {
            for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                List<String> fields = parseCsvLine(line);
                if (fields.size() >= 2 && !fields.getFirst().isBlank()) {
                    rows.put(fields.getFirst(), fields.get(1));
                }
            }
            backup(csv, backupRoot.resolve(Path.of("CD-Aware-AutoGather", "record", uid,
                    "采集目标.csv")));
        }
        for (CultivationExecutionProjection.GatherTarget target : targets) {
            rows.put("地方特产\\" + target.country() + "\\" + target.materialName(),
                    String.valueOf(target.required()));
        }
        Files.createDirectories(csv.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("物品,目标数量");
        rows.forEach((key, value) -> lines.add(csvField(key) + "," + csvField(value)));
        writeTextAtomically(csv, "\ufeff" + String.join("\r\n", lines) + "\r\n");
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        result.add(current.toString());
        return result;
    }

    private static String csvField(String value) {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void writeJsonAtomically(Path target, JsonNode value) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeTextAtomically(Path target, String value) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backup(Path source, Path backup) throws IOException {
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }
}
