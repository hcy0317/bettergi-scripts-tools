package com.cloud_guest.cultivation.execution.module;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScriptGroupSettingsExecutionModule implements CultivationExecutionModule {
    public static final String ID = "script-group-settings";

    @Override public String moduleId() { return ID; }
    @Override public String displayName() { return "一条龙配置组"; }
    @Override public String adapterVersion() { return "1.0"; }
    @Override public String description() { return "代管 BetterGI 配置组自身的跑图、战斗、周期与 Shell 设置"; }
    @Override public String integrationState() { return "已接入配置组根设置"; }
    @Override public List<String> capabilities() {
        return List.of("配置组队伍", "跑图与战斗", "执行周期", "前置配置组", "Shell");
    }

    @Override
    public List<CultivationModuleSettingField> settingsSchema() {
        return List.of(
                field("pathingEnabled", "启用配置组跑图设置", "switch"),
                field("partyName", "配置组队伍", "party-select", "uid-parties"),
                field("autoPickEnabled", "自动拾取", "switch"),
                field("mainAvatarIndex", "主要行走角色位置", "select", List.of("", "1", "2", "3", "4")),
                field("guardianAvatarIndex", "护盾角色位置", "select", List.of("", "1", "2", "3", "4")),
                field("guardianSkillIntervalSeconds", "护盾战技间隔（秒）", "text"),
                field("guardianSkillLongPress", "护盾战技长按", "switch"),
                field("visitStatueBeforeSwitchParty", "切队前前往七天神像", "switch"),
                field("onlyInTeleportRecover", "仅传送时复活", "switch"),
                field("jsScriptUseEnabled", "JS 脚本使用配置组跑图设置", "switch"),
                field("soloTaskUseFightEnabled", "JS 单任务使用配置组战斗设置", "switch"),
                field("skipDuring", "禁用时间段", "text"),
                field("useGadgetIntervalMs", "小道具使用间隔（毫秒）", "number"),
                field("autoSkipEnabled", "自动跳过剧情", "switch"),
                field("autoRunEnabled", "自动冲刺", "switch"),
                field("autoEatEnabled", "自动吃药", "switch"),
                field("hideOnRepeat", "连续执行时隐藏", "switch"),
                field("distance", "赶路通用临界距离", "number"),
                field("approachStopDistance", "接近停止距离", "number"),
                field("hurryOnAvatar", "赶路角色", "select",
                        List.of("", "自动", "玛薇卡", "闲云", "桑多涅", "恰斯卡", "流浪者", "伊法", "希诺宁", "法尔伽", "夜兰")),
                field("travelMode", "赶路模式", "select", List.of("精准靠近", "连续赶路")),
                field("switchToWalkEnabled", "接近节点时切人步行", "switch"),
                field("autoFightEnabled", "自动战斗", "switch"),
                field("autoFightStrategyName", "战斗策略", "text"),
                field("autoFightTeamNames", "战斗队伍限制", "text"),
                field("fightFinishDetectEnabled", "战斗结束识别", "switch"),
                field("pickDropsAfterFightEnabled", "战斗后拾取掉落", "switch"),
                field("pickDropsAfterFightSeconds", "战后拾取时长（秒）", "number"),
                field("fightTimeoutSeconds", "单场战斗超时（秒）", "number"),
                field("taskCycleEnabled", "启用配置组周期", "switch"),
                field("taskCycleBoundaryTime", "周期边界小时", "number"),
                field("taskCycleServerTime", "周期边界使用服务器时间", "switch"),
                field("taskCycleDays", "执行周期（天）", "number"),
                field("completionSkipEnabled", "完成后按规则跳过", "switch"),
                field("completionSkipLastRunGapSeconds", "完成后最短间隔（秒）", "number"),
                field("preExecutionPriorityEnabled", "优先执行其他配置组", "switch"),
                field("preExecutionPriorityGroupNames", "前置配置组名称", "text"),
                field("preExecutionPriorityMaxRetryCount", "前置配置组重试次数", "number"),
                field("shellEnabled", "启用 Shell 设置", "switch"),
                field("shellDisabled", "禁用 Shell 任务", "switch"),
                field("shellTimeoutSeconds", "Shell 超时（秒）", "number"),
                field("shellNoWindow", "隐藏 Shell 窗口", "switch"),
                field("shellOutput", "记录 Shell 输出", "switch"));
    }

    @Override
    public Map<String, Object> defaultSettings(String uid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pathingEnabled", true);
        result.put("partyName", "");
        result.put("autoPickEnabled", true);
        result.put("mainAvatarIndex", "1");
        result.put("guardianAvatarIndex", "1");
        result.put("guardianSkillIntervalSeconds", "");
        result.put("guardianSkillLongPress", true);
        result.put("visitStatueBeforeSwitchParty", false);
        result.put("onlyInTeleportRecover", false);
        result.put("jsScriptUseEnabled", true);
        result.put("soloTaskUseFightEnabled", true);
        result.put("skipDuring", "");
        result.put("useGadgetIntervalMs", 0);
        result.put("autoSkipEnabled", true);
        result.put("autoRunEnabled", true);
        result.put("autoEatEnabled", false);
        result.put("hideOnRepeat", false);
        result.put("distance", 45);
        result.put("approachStopDistance", 25);
        result.put("hurryOnAvatar", "");
        result.put("travelMode", "精准靠近");
        result.put("switchToWalkEnabled", false);
        result.put("autoFightEnabled", true);
        result.put("autoFightStrategyName", "根据队伍自动选择");
        result.put("autoFightTeamNames", "");
        result.put("fightFinishDetectEnabled", true);
        result.put("pickDropsAfterFightEnabled", true);
        result.put("pickDropsAfterFightSeconds", 60);
        result.put("fightTimeoutSeconds", 200);
        result.put("taskCycleEnabled", false);
        result.put("taskCycleBoundaryTime", 0);
        result.put("taskCycleServerTime", false);
        result.put("taskCycleDays", 1);
        result.put("completionSkipEnabled", false);
        result.put("completionSkipLastRunGapSeconds", -1);
        result.put("preExecutionPriorityEnabled", false);
        result.put("preExecutionPriorityGroupNames", "");
        result.put("preExecutionPriorityMaxRetryCount", 1);
        result.put("shellEnabled", false);
        result.put("shellDisabled", false);
        result.put("shellTimeoutSeconds", 60);
        result.put("shellNoWindow", true);
        result.put("shellOutput", true);
        return result;
    }

    private static CultivationModuleSettingField field(String key, String label, String control) {
        return new CultivationModuleSettingField(key, label, control, true, null);
    }

    private static CultivationModuleSettingField field(String key,
                                                       String label,
                                                       String control,
                                                       String optionsSource) {
        return new CultivationModuleSettingField(key, label, control, true, optionsSource);
    }

    private static CultivationModuleSettingField field(String key,
                                                       String label,
                                                       String control,
                                                       List<String> options) {
        return new CultivationModuleSettingField(key, label, control, true, null, options);
    }
}
