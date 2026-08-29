package com.cloud_guest.cultivation.execution.module;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloud_guest.cultivation.CultivationUid;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigEntity;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigMapper;
import com.cloud_guest.cultivation.execution.BetterGiInstalledScriptSettingsReader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CultivationModuleConfigurationService {
    private final CultivationModuleRegistry registry;
    private final CultivationModuleConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final BetterGiInstalledScriptSettingsReader installedSettingsReader;

    @Value("${server.port:8081}")
    private int serverPort = 8081;

    public CultivationModuleConfigurationService(CultivationModuleRegistry registry,
                                                  CultivationModuleConfigMapper mapper,
                                                  ObjectMapper objectMapper,
                                                  BetterGiInstalledScriptSettingsReader installedSettingsReader) {
        this.registry = registry;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.installedSettingsReader = installedSettingsReader;
    }

    public List<CultivationModuleConfiguration> findAll(String uid) {
        String normalizedUid = requireUid(uid);
        return registry.all().stream().map(module -> find(normalizedUid, module)).toList();
    }

    public CultivationModuleConfiguration find(String uid, String moduleId) {
        return find(requireUid(uid), registry.require(moduleId));
    }

    public CultivationModuleConfiguration find(String uid, CultivationExecutionModule module) {
        CultivationModuleConfigEntity entity = select(uid, module.moduleId());
        Map<String, Object> settings = initialSettings(module, uid);
        Optional<BetterGiInstalledScriptSettingsReader.InstalledScriptSettings> installed =
                installedSettingsReader.read(uid, module.moduleId());
        Instant storedModifiedAt = entity == null ? null : entityModifiedAt(entity);
        boolean installedWins = installed.isPresent()
                && (entity == null || storedModifiedAt == null
                    || installed.get().modifiedAt().isAfter(storedModifiedAt));
        if (installedWins) {
            if (entity != null && entity.getSettingsJson() != null && !entity.getSettingsJson().isBlank()) {
                mergeStored(settings, entity, module);
            }
            mergeSupported(settings, installed.get().settings(), module);
        } else if (entity != null && entity.getSettingsJson() != null && !entity.getSettingsJson().isBlank()) {
            mergeStored(settings, entity, module);
        }
        if (FullyAutoToolsExecutionModule.ID.equals(module.moduleId())) {
            Object selectedFamilies = settings.get("treeLevel_1_1");
            if (selectedFamilies instanceof List<?>) settings.put("routeFamilies", selectedFamilies);
        }
        applyReadOnlyDefaults(module, uid, settings);
        applyRuntimeSettings(module, settings);
        boolean enabled = installedWins && installed.get().enabled() != null
                ? installed.get().enabled()
                : entity == null ? module.defaultEnabled() : !Boolean.FALSE.equals(entity.getEnabled());
        if (installedWins) {
            persistInstalled(uid, module, entity, settings, enabled, installed.get().modifiedAt());
        }
        return new CultivationModuleConfiguration(CultivationModuleDefinition.from(module), enabled, settings);
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationModuleConfiguration save(String uid,
                                                String moduleId,
                                                CultivationModuleConfigurationRequest request) {
        String normalizedUid = requireUid(uid);
        CultivationExecutionModule module = registry.require(moduleId);
        CultivationModuleConfigEntity existing = select(normalizedUid, moduleId);
        CultivationModuleConfiguration current = find(normalizedUid, module);
        Map<String, Object> settings = new LinkedHashMap<>(current.settings());
        Set<String> allowedKeys = module.settingsSchema().stream()
                .map(CultivationModuleSettingField::key)
                .collect(Collectors.toSet());
        if (request.settings() != null) {
            request.settings().forEach((key, value) -> {
                if (!allowedKeys.contains(key)) {
                    throw new IllegalArgumentException("模块设置项不存在：" + moduleId + "." + key);
                }
                settings.put(key, value);
            });
        }
        applyReadOnlyDefaults(module, normalizedUid, settings);
        applyRuntimeSettings(module, settings);

        try {
            CultivationModuleConfigEntity entity = existing == null
                    ? new CultivationModuleConfigEntity() : existing;
            entity.setUid(normalizedUid);
            entity.setModuleId(moduleId);
            entity.setAdapterVersion(module.adapterVersion());
            entity.setEnabled(request.enabled() == null
                    ? current.enabled()
                    : request.enabled());
            entity.setSettingsJson(objectMapper.writeValueAsString(settings));
            Instant installedModifiedAt = installedSettingsReader.read(normalizedUid, moduleId)
                    .map(BetterGiInstalledScriptSettingsReader.InstalledScriptSettings::modifiedAt)
                    .orElse(null);
            entity.setUpdateTime(nextModifiedAt(installedModifiedAt, entity.getUpdateTime()));
            if (existing == null) mapper.insert(entity); else mapper.updateById(entity);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存模块设置：" + moduleId, exception);
        }
        return find(normalizedUid, module);
    }

    private CultivationModuleConfigEntity select(String uid, String moduleId) {
        return mapper.selectOne(Wrappers.lambdaQuery(CultivationModuleConfigEntity.class)
                .eq(CultivationModuleConfigEntity::getUid, uid)
                .eq(CultivationModuleConfigEntity::getModuleId, moduleId));
    }

    private Map<String, Object> initialSettings(CultivationExecutionModule module, String uid) {
        return new LinkedHashMap<>(module.defaultSettings(uid));
    }

    private void mergeStored(Map<String, Object> target,
                             CultivationModuleConfigEntity entity,
                             CultivationExecutionModule module) {
        try {
            Map<String, Object> stored = objectMapper.readValue(
                    entity.getSettingsJson(), new TypeReference<>() {});
            mergeSupported(target, stored, module);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取模块设置：" + module.moduleId(), exception);
        }
    }

    private static void mergeSupported(Map<String, Object> target,
                                       Map<String, Object> source,
                                       CultivationExecutionModule module) {
        Set<String> supportedKeys = module.settingsSchema().stream()
                .map(CultivationModuleSettingField::key)
                .collect(Collectors.toSet());
        source.forEach((key, value) -> {
            if (supportedKeys.contains(key)) target.put(key, value);
        });
    }

    private static Instant entityModifiedAt(CultivationModuleConfigEntity entity) {
        var modifiedAt = entity.getUpdateTime() != null ? entity.getUpdateTime() : entity.getCreateTime();
        return modifiedAt == null ? null : modifiedAt.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void persistInstalled(String uid,
                                  CultivationExecutionModule module,
                                  CultivationModuleConfigEntity existing,
                                  Map<String, Object> settings,
                                  boolean enabled,
                                  Instant fileModifiedAt) {
        try {
            CultivationModuleConfigEntity entity = existing == null
                    ? new CultivationModuleConfigEntity() : existing;
            entity.setUid(uid);
            entity.setModuleId(module.moduleId());
            entity.setAdapterVersion(module.adapterVersion());
            entity.setEnabled(enabled);
            entity.setSettingsJson(objectMapper.writeValueAsString(settings));
            entity.setUpdateTime(nextModifiedAt(fileModifiedAt, entity.getUpdateTime()));
            if (existing == null) mapper.insert(entity); else mapper.updateById(entity);
        } catch (Exception exception) {
            throw new IllegalStateException("无法吸收 BetterGI 较新的模块设置：" + module.moduleId(), exception);
        }
    }

    private static LocalDateTime nextModifiedAt(Instant externalModifiedAt,
                                                LocalDateTime existingModifiedAt) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime candidate = LocalDateTime.now();
        if (externalModifiedAt != null) {
            LocalDateTime external = LocalDateTime.ofInstant(externalModifiedAt, zone);
            if (!candidate.isAfter(external)) candidate = external.plusNanos(1);
        }
        if (existingModifiedAt != null && !candidate.isAfter(existingModifiedAt)) {
            candidate = existingModifiedAt.plusNanos(1);
        }
        return candidate;
    }

    private void applyRuntimeSettings(CultivationExecutionModule module, Map<String, Object> settings) {
        String backend = "http://127.0.0.1:" + serverPort + "/bgi";
        if (AutoPlanResinExecutionModule.ID.equals(module.moduleId())) {
            settings.put("bgi_tools_http_pull_json_config", backend + "/auto/plan/json");
            settings.put("bgi_tools_http_push_all_json_config", backend + "/auto/plan/domain/json/all");
            settings.put("bgi_tools_http_push_all_country_config", backend + "/auto/plan/country/json/all");
            settings.put("bgi_tools_http_push_all_boss_config", backend + "/auto/plan/boss/json/all");
        }
        if (FullyAutoToolsExecutionModule.ID.equals(module.moduleId())) {
            settings.put("http_api", backend + "/cron/next-timestamp/all");
        }
    }

    private static void applyReadOnlyDefaults(CultivationExecutionModule module,
                                              String uid,
                                              Map<String, Object> settings) {
        Map<String, Object> defaults = module.defaultSettings(uid);
        module.settingsSchema().stream()
                .filter(field -> !field.editable())
                .forEach(field -> settings.put(field.key(), defaults.get(field.key())));
    }

    private static String requireUid(String uid) {
        return CultivationUid.normalize(uid);
    }
}
