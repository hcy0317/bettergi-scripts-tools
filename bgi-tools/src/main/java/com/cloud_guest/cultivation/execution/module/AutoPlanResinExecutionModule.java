package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AutoPlanResinExecutionModule implements CultivationExecutionModule {
    public static final String ID = "auto-plan-resin";

    @Override public String moduleId() { return ID; }
    @Override public String displayName() { return "自动体力计划"; }
    @Override public String adapterVersion() { return "1.1"; }
    @Override public String description() { return "秘境、地脉与首领的单轮行动入口"; }
    @Override public String integrationState() { return "已接入行动投影"; }
    @Override public List<String> capabilities() { return List.of("秘境", "地脉", "世界首领", "执行后重规划"); }

    @Override
    public List<CultivationModuleSettingField> settingsSchema() {
        return List.of(
                new CultivationModuleSettingField("key", "AutoPlan 脚本密钥", "text", false, null),
                new CultivationModuleSettingField("auto_check", "运行前检查", "multi-select", true, null,
                        List.of("幽境检查", "圣遗物空间检查")),
                new CultivationModuleSettingField("holy_relic_threshold", "圣遗物剩余空间阈值", "number", true, null),
                new CultivationModuleSettingField("auto_load", "计划加载来源", "multi-select", true, null,
                        List.of("输入加载", "UID加载", "bgi_tools加载")),
                new CultivationModuleSettingField("exclude_run_exception", "单项异常后继续下一项", "switch", true, null),
                new CultivationModuleSettingField("loop_plan", "循环体力计划", "switch", true, null),
                new CultivationModuleSettingField("retry_count", "脚本复活重试次数", "select", true, null,
                        List.of("1", "2", "3", "4", "5", "6", "7", "8", "9")),
                new CultivationModuleSettingField("bgi_tools_http_pull_json_config", "拉取计划接口", "text", false, null),
                new CultivationModuleSettingField("bgi_tools_open_push", "推送基础常量", "switch", true, null),
                new CultivationModuleSettingField("bgi_tools_http_push_all_json_config", "秘境常量接口", "text", false, null),
                new CultivationModuleSettingField("bgi_tools_http_push_all_country_config", "国家常量接口", "text", false, null),
                new CultivationModuleSettingField("bgi_tools_http_push_all_boss_config", "首领常量接口", "text", false, null),
                new CultivationModuleSettingField("bgi_tools_token", "工具集授权", "text", true, null),
                new CultivationModuleSettingField("debug", "开发者模式", "switch", true, null),
                new CultivationModuleSettingField("talentDomainEnabled", "天赋书秘境", "switch", true, null),
                new CultivationModuleSettingField("moraLeyLineEnabled", "摩拉地脉", "switch", true, null),
                new CultivationModuleSettingField("experienceLeyLineEnabled", "大英雄经验地脉", "switch", true, null),
                new CultivationModuleSettingField("partyName", "秘境与地脉队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("leyLineCountry", "地脉地区", "select", true, null,
                        List.of("蒙德", "璃月", "稻妻", "须弥", "枫丹", "纳塔", "挪德卡莱")),
                new CultivationModuleSettingField("bossPartyName", "首领讨伐队伍", "party-select", true, "uid-parties"),
                new CultivationModuleSettingField("bossStrategyName", "首领战斗策略", "text", true, null),
                new CultivationModuleSettingField("bossReviveRetryCount", "首领复苏重试次数", "number", true, null),
                new CultivationModuleSettingField("bossReturnToStatueAfterEachRound", "每轮后返回七天神像", "switch", true, null),
                new CultivationModuleSettingField("bossRewardRecognitionEnabled", "启用奖励识别", "switch", true, null),
                new CultivationModuleSettingField("bossTimeoutSeconds", "单轮首领超时（秒）", "number", true, null),
                new CultivationModuleSettingField("replanAfterEachAction", "每轮执行后重新规划", "switch", false, null));
    }

    @Override
    public Map<String, Object> defaultSettings(String uid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", "2Cbayi1S2I41BlTXb/XAmw==");
        result.put("auto_check", List.of());
        result.put("holy_relic_threshold", 100);
        result.put("auto_load", List.of("bgi_tools加载"));
        result.put("exclude_run_exception", true);
        result.put("loop_plan", false);
        result.put("retry_count", "3");
        result.put("bgi_tools_http_pull_json_config", "http://127.0.0.1:18081/bgi/auto/plan/json");
        result.put("bgi_tools_open_push", false);
        result.put("bgi_tools_http_push_all_json_config", "http://127.0.0.1:18081/bgi/auto/plan/domain/json/all");
        result.put("bgi_tools_http_push_all_country_config", "http://127.0.0.1:18081/bgi/auto/plan/country/json/all");
        result.put("bgi_tools_http_push_all_boss_config", "http://127.0.0.1:18081/bgi/auto/plan/boss/json/all");
        result.put("bgi_tools_token", "Authorization= ");
        result.put("debug", false);
        result.put("talentDomainEnabled", true);
        result.put("moraLeyLineEnabled", true);
        result.put("experienceLeyLineEnabled", true);
        result.put("partyName", "");
        result.put("leyLineCountry", "挪德卡莱");
        result.put("bossPartyName", "");
        result.put("bossStrategyName", "根据队伍自动选择");
        result.put("bossReviveRetryCount", 5);
        result.put("bossReturnToStatueAfterEachRound", false);
        result.put("bossRewardRecognitionEnabled", true);
        result.put("bossTimeoutSeconds", 300);
        result.put("replanAfterEachAction", true);
        return result;
    }
}
