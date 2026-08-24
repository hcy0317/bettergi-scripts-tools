package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CdAwareAutoGatherExecutionModule implements CultivationExecutionModule {
    public static final String ID = "cd-aware-auto-gather";

    @Override public String moduleId() { return ID; }
    @Override public String displayName() { return "冷却感知自动采集"; }
    @Override public String adapterVersion() { return "1.1"; }
    @Override public String description() { return "地方特产按冷却、库存目标和路线执行"; }
    @Override public String integrationState() { return "已生成脚本设置与目标数据"; }
    @Override public List<String> capabilities() { return List.of("地方特产", "冷却判断", "按库存目标停止"); }

    @Override
    public List<CultivationModuleSettingField> settingsSchema() {
        return List.of(
                new CultivationModuleSettingField("runMode", "运行模式", "select", false, null,
                        List.of("扫描文件夹更新可选材料列表", "采集选中的材料")),
                new CultivationModuleSettingField("filterPathByKeywords", "路线关键词筛选", "text", true, null),
                new CultivationModuleSettingField("partyName", "采集首选队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("partyName2nd", "采集备选队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("excludeTimeRange", "禁用时间段", "text", true, null),
                new CultivationModuleSettingField("targetCountOfSelected", "材料目标模式", "text", false, null),
                new CultivationModuleSettingField("manualSetAccountName", "记录账号", "text", false, null),
                new CultivationModuleSettingField("selectByCategory", "按大类选择", "multi-select", true, null,
                        List.of("地方特产", "矿物", "食材与炼金")),
                new CultivationModuleSettingField("selectLocalSpecialtyByCountry", "按地区选择地方特产", "multi-select", true, null,
                        List.of("蒙德", "璃月", "稻妻", "须弥", "枫丹", "纳塔", "挪德卡莱", "至冬")),
                new CultivationModuleSettingField("selectForgingOre", "矿物", "multi-select", true, null,
                        List.of("铁块", "白铁块", "水晶块", "星银矿石", "紫晶块", "萃凝晶", "虹滴晶", "莉奈娅一条龙")),
                new CultivationModuleSettingField("selectMiscellaneous", "特殊物品", "multi-select", true, null,
                        List.of("晶蝶", "沉玉仙茗", "瓦雷莎家的果园", "纳塔食材一条龙", "提瓦特食材一条龙")),
                new CultivationModuleSettingField("selectFoodAndAlchemy", "食材与炼金", "multi-select", true, null,
                        List.of("晶蝶")));
    }

    @Override
    public Map<String, Object> defaultSettings(String uid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runMode", "采集选中的材料");
        result.put("filterPathByKeywords", "低成功率 不建议");
        result.put("partyName", "");
        result.put("partyName2nd", "");
        result.put("excludeTimeRange", "");
        result.put("targetCountOfSelected", "csv");
        result.put("manualSetAccountName", uid);
        result.put("selectByCategory", List.of());
        result.put("selectLocalSpecialtyByCountry", List.of());
        result.put("selectForgingOre", List.of());
        result.put("selectMiscellaneous", List.of());
        result.put("selectFoodAndAlchemy", List.of());
        return result;
    }
}
