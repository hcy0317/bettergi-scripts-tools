package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FullyAutoToolsExecutionModule implements CultivationExecutionModule {
    public static final String ID = "fully-auto-and-semi-auto-tools";

    @Override public String moduleId() { return ID; }
    @Override public String displayName() { return "FullyAutoAndSemiAutoTools 怪物路线"; }
    @Override public String adapterVersion() { return "1.0"; }
    @Override public String description() { return "按材料缺口选择敌人与魔物路线并复用脚本的 CD、队伍和限次配置"; }
    @Override public String integrationState() { return "已接入怪物材料目录与脚本组同步"; }
    @Override public List<String> capabilities() { return List.of("怪物掉落", "路线选择", "CD 判断", "队伍切换"); }

    @Override
    public List<CultivationModuleSettingField> settingsSchema() {
        return List.of(
                new CultivationModuleSettingField("key", "脚本密钥", "text", false, null),
                new CultivationModuleSettingField("config_run", "配置模式", "select", false, null,
                        List.of("刷新", "加载", "执行")),
                new CultivationModuleSettingField("refresh_record", "清空运行记录", "switch", true, null),
                new CultivationModuleSettingField("refresh_record_mode", "清空记录范围", "select", true, null,
                        List.of("全部", "UID")),
                new CultivationModuleSettingField("loading_level", "加载路径层级", "number", true, null),
                new CultivationModuleSettingField("the_layer", "只加载指定层级", "switch", true, null),
                new CultivationModuleSettingField("high_level_filtering", "高阶过滤", "text", true, null),
                new CultivationModuleSettingField("order_rules", "执行顺序规则", "text", true, null),
                new CultivationModuleSettingField("config_white_list", "刷新白名单", "text", true, null),
                new CultivationModuleSettingField("config_black_list", "刷新黑名单", "text", true, null),
                new CultivationModuleSettingField("open_cd", "启用 CD 算法", "switch", true, null),
                new CultivationModuleSettingField("http_api", "CD 算法接口", "text", false, null),
                new CultivationModuleSettingField("real_time_missions", "实时任务", "multi-select", true, null,
                        List.of("自动对话", "自动战斗(已弃用)", "自动拾取")),
                new CultivationModuleSettingField("choose_best", "择优模式", "switch", true, null),
                new CultivationModuleSettingField("mode", "执行模式", "select", true, null,
                        List.of("全自动", "半自动")),
                new CultivationModuleSettingField("auto_semi_key_mode", "半自动快捷键模式", "select", true, null,
                        List.of("继续运行", "跳过")),
                new CultivationModuleSettingField("auto_key", "半自动快捷键", "text", true, null),
                new CultivationModuleSettingField("open_limit_max", "启用路径数量上限", "switch", true, null),
                new CultivationModuleSettingField("limit_max_group", "执行组最大路径数", "text", true, null),
                new CultivationModuleSettingField("team_fight", "行走与战斗队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("team_hoe_ground", "锄地队伍规则", "text", true, null),
                new CultivationModuleSettingField("team_seven_elements", "七元素队伍", "text", true, null),
                new CultivationModuleSettingField("routeFamilies", "养成缺口怪物路线", "multi-select", false,
                        "monster-route-families"));
    }

    @Override
    public Map<String, Object> defaultSettings(String uid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", "PGCSBY37NJA");
        result.put("config_run", "执行");
        result.put("refresh_record", false);
        result.put("refresh_record_mode", "UID");
        result.put("loading_level", 3);
        result.put("the_layer", false);
        result.put("high_level_filtering", "");
        result.put("order_rules", "");
        result.put("config_white_list", "敌人与魔物");
        result.put("config_black_list", "其他,地方特产,矿物,食材与炼金,晶蝶,传奇,低效,不跑,不刷,不稳定,钓鱼,木材,成就");
        result.put("open_cd", true);
        result.put("http_api", "http://127.0.0.1:8081/bgi/cron/next-timestamp/all");
        result.put("real_time_missions", List.of("自动对话", "自动拾取"));
        result.put("choose_best", true);
        result.put("mode", "全自动");
        result.put("auto_semi_key_mode", "继续运行");
        result.put("auto_key", "F8");
        result.put("open_limit_max", false);
        result.put("limit_max_group", "");
        result.put("team_fight", "");
        result.put("team_hoe_ground", "");
        result.put("team_seven_elements", "");
        result.put("routeFamilies", List.of());
        return result;
    }
}
