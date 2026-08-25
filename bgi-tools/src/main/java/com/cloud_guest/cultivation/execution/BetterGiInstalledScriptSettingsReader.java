package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.cloud_guest.cultivation.execution.module.ScriptGroupSettingsExecutionModule;
import com.cloud_guest.cultivation.execution.module.WeeklyBossExecutionModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class BetterGiInstalledScriptSettingsReader {
    private static final Map<String, Set<String>> ALIASES = Map.of(
            AutoPlanResinExecutionModule.ID, Set.of("AutoPlan"),
            CdAwareAutoGatherExecutionModule.ID, Set.of("CD-Aware-AutoGather"),
            WeeklyBossExecutionModule.ID, Set.of("WeeklyBoss"),
            FullyAutoToolsExecutionModule.ID,
            Set.of("FullyAutoAndSemiAutoTools", "HCY-FullyAutoAndSemiAutoTools"));
    private static final Map<String, String> GROUP_SETTING_PATHS = Map.ofEntries(
            Map.entry("pathingEnabled", "/config/pathingConfig/enabled"),
            Map.entry("partyName", "/config/pathingConfig/partyName"),
            Map.entry("autoPickEnabled", "/config/pathingConfig/autoPickEnabled"),
            Map.entry("mainAvatarIndex", "/config/pathingConfig/mainAvatarIndex"),
            Map.entry("guardianAvatarIndex", "/config/pathingConfig/guardianAvatarIndex"),
            Map.entry("guardianSkillIntervalSeconds", "/config/pathingConfig/guardianElementalSkillSecondInterval"),
            Map.entry("guardianSkillLongPress", "/config/pathingConfig/guardianElementalSkillLongPress"),
            Map.entry("visitStatueBeforeSwitchParty", "/config/pathingConfig/isVisitStatueBeforeSwitchParty"),
            Map.entry("onlyInTeleportRecover", "/config/pathingConfig/onlyInTeleportRecover"),
            Map.entry("jsScriptUseEnabled", "/config/pathingConfig/jsScriptUseEnabled"),
            Map.entry("soloTaskUseFightEnabled", "/config/pathingConfig/soloTaskUseFightEnabled"),
            Map.entry("skipDuring", "/config/pathingConfig/skipDuring"),
            Map.entry("useGadgetIntervalMs", "/config/pathingConfig/useGadgetIntervalMs"),
            Map.entry("autoSkipEnabled", "/config/pathingConfig/autoSkipEnabled"),
            Map.entry("autoRunEnabled", "/config/pathingConfig/autoRunEnabled"),
            Map.entry("autoEatEnabled", "/config/pathingConfig/autoEatEnabled"),
            Map.entry("hideOnRepeat", "/config/pathingConfig/hideOnRepeat"),
            Map.entry("distance", "/config/pathingConfig/distance"),
            Map.entry("approachStopDistance", "/config/pathingConfig/approachStopDistance"),
            Map.entry("hurryOnAvatar", "/config/pathingConfig/hurryOnAvatar"),
            Map.entry("travelMode", "/config/pathingConfig/travelMode"),
            Map.entry("switchToWalkEnabled", "/config/pathingConfig/switchToWalkEnabled"),
            Map.entry("autoFightEnabled", "/config/pathingConfig/autoFightEnabled"),
            Map.entry("autoFightStrategyName", "/config/pathingConfig/autoFightConfig/strategyName"),
            Map.entry("autoFightTeamNames", "/config/pathingConfig/autoFightConfig/teamNames"),
            Map.entry("fightFinishDetectEnabled", "/config/pathingConfig/autoFightConfig/fightFinishDetectEnabled"),
            Map.entry("pickDropsAfterFightEnabled", "/config/pathingConfig/autoFightConfig/pickDropsAfterFightEnabled"),
            Map.entry("pickDropsAfterFightSeconds", "/config/pathingConfig/autoFightConfig/pickDropsAfterFightSeconds"),
            Map.entry("fightTimeoutSeconds", "/config/pathingConfig/autoFightConfig/timeout"),
            Map.entry("taskCycleEnabled", "/config/pathingConfig/taskCycleConfig/enable"),
            Map.entry("taskCycleBoundaryTime", "/config/pathingConfig/taskCycleConfig/boundaryTime"),
            Map.entry("taskCycleServerTime", "/config/pathingConfig/taskCycleConfig/isBoundaryTimeBasedOnServerTime"),
            Map.entry("taskCycleDays", "/config/pathingConfig/taskCycleConfig/cycle"),
            Map.entry("completionSkipEnabled", "/config/pathingConfig/taskCompletionSkipRuleConfig/enable"),
            Map.entry("completionSkipLastRunGapSeconds", "/config/pathingConfig/taskCompletionSkipRuleConfig/lastRunGapSeconds"),
            Map.entry("preExecutionPriorityEnabled", "/config/pathingConfig/preExecutionPriorityConfig/enabled"),
            Map.entry("preExecutionPriorityGroupNames", "/config/pathingConfig/preExecutionPriorityConfig/groupNames"),
            Map.entry("preExecutionPriorityMaxRetryCount", "/config/pathingConfig/preExecutionPriorityConfig/maxRetryCount"),
            Map.entry("shellEnabled", "/config/enableShellConfig"),
            Map.entry("shellDisabled", "/config/shellConfig/disable"),
            Map.entry("shellTimeoutSeconds", "/config/shellConfig/timeout"),
            Map.entry("shellNoWindow", "/config/shellConfig/noWindow"),
            Map.entry("shellOutput", "/config/shellConfig/output"));

    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private final ObjectMapper objectMapper;

    public BetterGiInstalledScriptSettingsReader(CultivationMaterialSourceCatalog materialSourceCatalog,
                                                 ObjectMapper objectMapper) {
        this.materialSourceCatalog = materialSourceCatalog;
        this.objectMapper = objectMapper;
    }

    public Optional<InstalledScriptSettings> read(String uid, String moduleId) {
        Set<String> aliases = ALIASES.get(moduleId);
        boolean groupRoot = ScriptGroupSettingsExecutionModule.ID.equals(moduleId);
        if ((!groupRoot && aliases == null) || uid == null || uid.isBlank()) return Optional.empty();
        String normalizedUid = uid.trim();
        if (!normalizedUid.matches("\\d+")) return Optional.empty();
        Path groupFile;
        try {
            groupFile = materialSourceCatalog.betterGiRoot().resolve(Path.of(
                    "User", "ScriptGroup", "养成一条龙-" + normalizedUid + ".json"));
        } catch (IllegalStateException exception) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(groupFile)) return Optional.empty();

        try {
            JsonNode group = objectMapper.readTree(groupFile.toFile());
            if (groupRoot) {
                return Optional.of(new InstalledScriptSettings(
                        readGroupSettings(group), null, Files.getLastModifiedTime(groupFile).toInstant()));
            }
            JsonNode project = findProject(group, aliases);
            if (project == null || !project.path("jsScriptSettingsObject").isObject()) {
                return Optional.empty();
            }
            Map<String, Object> settings = objectMapper.convertValue(
                    project.path("jsScriptSettingsObject"),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            String status = project.path("status").asText("");
            Boolean enabled = status.isBlank() ? null : !"Disabled".equalsIgnoreCase(status);
            return Optional.of(new InstalledScriptSettings(
                    settings, enabled, Files.getLastModifiedTime(groupFile).toInstant()));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 BetterGI UID 专属脚本设置", exception);
        }
    }

    private Map<String, Object> readGroupSettings(JsonNode group) {
        Map<String, Object> settings = new LinkedHashMap<>();
        GROUP_SETTING_PATHS.forEach((key, pointer) -> {
            JsonNode value = group.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                settings.put(key, objectMapper.convertValue(value, Object.class));
            }
        });
        return settings;
    }

    private static JsonNode findProject(JsonNode node, Set<String> aliases) {
        if (node.isObject() && aliases.contains(node.path("folderName").asText())) return node;
        for (JsonNode child : node) {
            JsonNode found = findProject(child, aliases);
            if (found != null) return found;
        }
        return null;
    }

    public record InstalledScriptSettings(
            Map<String, Object> settings,
            Boolean enabled,
            Instant modifiedAt
    ) {
        public InstalledScriptSettings {
            settings = settings == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }
    }
}
