package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class BetterGiInstalledScriptSettingsReader {
    private static final Map<String, Set<String>> ALIASES = Map.of(
            AutoPlanResinExecutionModule.ID, Set.of("AutoPlan"),
            CdAwareAutoGatherExecutionModule.ID, Set.of("CD-Aware-AutoGather"),
            FullyAutoToolsExecutionModule.ID,
            Set.of("FullyAutoAndSemiAutoTools", "HCY-FullyAutoAndSemiAutoTools"));

    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private final ObjectMapper objectMapper;

    public BetterGiInstalledScriptSettingsReader(CultivationMaterialSourceCatalog materialSourceCatalog,
                                                 ObjectMapper objectMapper) {
        this.materialSourceCatalog = materialSourceCatalog;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> read(String moduleId) {
        Set<String> aliases = ALIASES.get(moduleId);
        if (aliases == null) return Map.of();
        Path scriptGroups;
        try {
            scriptGroups = materialSourceCatalog.betterGiRoot().resolve(Path.of("User", "ScriptGroup"));
        } catch (IllegalStateException exception) {
            return Map.of();
        }
        if (!Files.isDirectory(scriptGroups)) return Map.of();

        try (var files = Files.list(scriptGroups)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                JsonNode settings = findSettings(objectMapper.readTree(file.toFile()), aliases);
                if (settings != null) {
                    return objectMapper.convertValue(settings, new TypeReference<LinkedHashMap<String, Object>>() {});
                }
            }
            return Map.of();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI 当前脚本设置", exception);
        }
    }

    private static JsonNode findSettings(JsonNode node, Set<String> aliases) {
        if (node.isObject()
                && aliases.contains(node.path("folderName").asText())
                && node.path("jsScriptSettingsObject").isObject()) {
            return node.path("jsScriptSettingsObject");
        }
        for (JsonNode child : node) {
            JsonNode found = findSettings(child, aliases);
            if (found != null) return found;
        }
        return null;
    }
}
