package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Component
public class CultivationModuleRegistry {
    private final Map<String, CultivationExecutionModule> modules;

    public CultivationModuleRegistry(List<CultivationExecutionModule> registeredModules) {
        Map<String, CultivationExecutionModule> collected = new LinkedHashMap<>();
        for (CultivationExecutionModule module : registeredModules) {
            if (collected.putIfAbsent(module.moduleId(), module) != null) {
                throw new IllegalStateException("养成执行模块重复注册：" + module.moduleId());
            }
        }
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(collected));
    }

    public List<CultivationExecutionModule> all() {
        return List.copyOf(modules.values());
    }

    public CultivationExecutionModule require(String moduleId) {
        CultivationExecutionModule module = modules.get(moduleId);
        if (module == null) throw new IllegalArgumentException("未知养成执行模块：" + moduleId);
        return module;
    }
}
