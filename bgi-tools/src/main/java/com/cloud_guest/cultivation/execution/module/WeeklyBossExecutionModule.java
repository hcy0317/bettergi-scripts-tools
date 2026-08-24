package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WeeklyBossExecutionModule implements CultivationExecutionModule {
    public static final String ID = "weekly-boss";

    @Override public String moduleId() { return ID; }
    @Override public String displayName() { return "WeeklyBoss 周本一条龙"; }
    @Override public String adapterVersion() { return "1.0"; }
    @Override public String description() { return "周本材料缺口对应到周本脚本，单轮执行后重新确认库存"; }
    @Override public String integrationState() { return "已接入 UID 专属脚本组"; }
    @Override public List<String> capabilities() { return List.of("周本", "队伍切换", "料理", "能量恢复"); }

    @Override
    public List<CultivationModuleSettingField> settingsSchema() {
        return List.of(
                new CultivationModuleSettingField("difficulty", "难度等级", "select", true, null,
                        List.of("1", "2", "3", "4")),
                new CultivationModuleSettingField("ifEarlyChallage", "地图未解锁时提前挑战", "switch", true, null),
                new CultivationModuleSettingField("challengeTime", "单轮战斗时长（秒）", "number", true, null),
                new CultivationModuleSettingField("teamName", "周本挑战队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("foodName", "攻击/药剂/防御料理", "text", true, null),
                new CultivationModuleSettingField("resurgenceFoodName", "复活料理", "text", true, null),
                new CultivationModuleSettingField("recoveryFoodName", "复活后回血料理", "text", true, null),
                new CultivationModuleSettingField("ifAutoEatFood", "红血自动吃药", "switch", true, null),
                new CultivationModuleSettingField("energyMax", "挑战前恢复满能量", "switch", true, null),
                new CultivationModuleSettingField("fightMode", "忽略刷新周期与体力", "switch", true, null),
                new CultivationModuleSettingField("unfairContractTerms", "确认周本脚本风险条款", "switch", true, null));
    }

    @Override
    public Map<String, Object> defaultSettings(String uid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("difficulty", "4");
        result.put("ifEarlyChallage", false);
        result.put("challengeTime", 60);
        result.put("teamName", "");
        result.put("foodName", "");
        result.put("resurgenceFoodName", "");
        result.put("recoveryFoodName", "");
        result.put("ifAutoEatFood", false);
        result.put("energyMax", false);
        result.put("fightMode", false);
        result.put("unfairContractTerms", false);
        return result;
    }
}
