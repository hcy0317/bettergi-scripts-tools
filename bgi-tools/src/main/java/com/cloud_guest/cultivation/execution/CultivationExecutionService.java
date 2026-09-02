package com.cloud_guest.cultivation.execution;

import cn.hutool.core.util.StrUtil;
import com.cloud_guest.cultivation.CultivationUid;
import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfiguration;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationRequest;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.ScriptGroupSettingsExecutionModule;
import com.cloud_guest.cultivation.execution.module.WeeklyBossExecutionModule;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanApplicationService;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.cloud_guest.entitys.common.auto_plan.AutoPlan;
import com.cloud_guest.service.AutoPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CultivationExecutionService {
    private static final List<String> RESIN_SOURCES =
            List.of("浓缩树脂", "原粹树脂", "须臾树脂", "脆弱树脂");
    private static final String EXECUTION_MODE = "计划驱动：领取一个行动，权威库存回写后重新规划";
    private static final Map<String, String> MANUAL_MATERIALS = Map.of(
            "智识之冕", "人工来源：活动、版本奖励等限量渠道；系统持续保留缺口，取得后重新导入确认");

    private final CultivationPlanApplicationService planService;
    private final CultivationLedgerObservationService observationService;
    private final AutoPlanService autoPlanService;
    private final CultivationModuleConfigurationService configurationService;
    private final CultivationMaterialSourceCatalog materialSourceCatalog;
    private final BetterGiCombatOptionCatalog combatOptionCatalog;

    public CultivationExecutionService(CultivationPlanApplicationService planService,
                                       CultivationLedgerObservationService observationService,
                                       AutoPlanService autoPlanService,
                                       CultivationModuleConfigurationService configurationService,
                                       CultivationMaterialSourceCatalog materialSourceCatalog,
                                       BetterGiCombatOptionCatalog combatOptionCatalog) {
        this.planService = planService;
        this.observationService = observationService;
        this.autoPlanService = autoPlanService;
        this.configurationService = configurationService;
        this.materialSourceCatalog = materialSourceCatalog;
        this.combatOptionCatalog = combatOptionCatalog;
    }

    public CultivationExecutionProjection projection(String uid) {
        String normalizedUid = requireUid(uid);
        CultivationPlanRevisionResponse revision = latestLedger(normalizedUid);
        if (revision == null) {
            return null;
        }

        CultivationExecutionPreferences preferences = preferences(normalizedUid);
        CultivationMaterialCraftingPlan plannedCrafting = observationService.craftingPlan(revision.requirements());
        CultivationMaterialCraftingPlan craftingPlan = plannedCrafting == null
                ? new CultivationMaterialCraftingPlan(Map.of(), List.of())
                : plannedCrafting;
        List<CultivationLedgerEntry> projectedRequirements = revision.requirements().stream()
                .map(entry -> new CultivationLedgerEntry(
                        entry.sourceIndex(), entry.materialName(), entry.required(), entry.baselineOwned(),
                        entry.currentOwned(), craftingPlan.remainingByMaterial().getOrDefault(
                                entry.materialName(), entry.remaining()),
                        entry.remainingEvidence(), entry.ocrConfidence(), entry.manuallyCorrected(),
                        entry.sourceBlocks()))
                .toList();
        CultivationModuleConfiguration autoPlanConfiguration = configurationService.find(
                normalizedUid, AutoPlanResinExecutionModule.ID);
        CultivationModuleConfiguration gatherConfiguration = configurationService.find(
                normalizedUid, CdAwareAutoGatherExecutionModule.ID);
        CultivationModuleConfiguration monsterConfiguration = configurationService.find(
                normalizedUid, FullyAutoToolsExecutionModule.ID);
        CultivationModuleConfiguration weeklyBossConfiguration = configurationService.find(
                normalizedUid, WeeklyBossExecutionModule.ID);
        List<Map<String, Object>> domains = effectiveDomains(autoPlanService.findDomainAll());
        List<CultivationExecutionProjection.ResinAction> resinActions = new ArrayList<>();
        List<CultivationExecutionProjection.BossAction> bossActions = new ArrayList<>();
        List<CultivationExecutionProjection.WeeklyBossAction> weeklyBossActions = new ArrayList<>();
        List<CultivationExecutionProjection.GatherTarget> gatherTargets = new ArrayList<>();
        List<CultivationExecutionProjection.MonsterTarget> monsterTargets = new ArrayList<>();
        List<CultivationExecutionProjection.PendingMaterial> pending = new ArrayList<>();

        for (CultivationLedgerEntry entry : projectedRequirements) {
            if (entry.remaining() <= 0) {
                continue;
            }
            if ("摩拉".equals(entry.materialName()) || "大英雄的经验".equals(entry.materialName())) {
                resinActions.add(new CultivationExecutionProjection.ResinAction(
                        entry.materialName(), entry.remaining(), "地脉",
                        "摩拉".equals(entry.materialName()) ? "藏金之花" : "启示之花",
                        "经验与摩拉", preferences.domainParty(),
                        "可生成下一步行动",
                        null, entry.materialName(), List.of()));
                continue;
            }

            Optional<DomainMatch> domain = findDomain(entry.materialName(), domains);
            if (domain.isPresent()) {
                DomainMatch match = domain.get();
                resinActions.add(new CultivationExecutionProjection.ResinAction(
                        entry.materialName(), entry.remaining(), "秘境", match.name(), match.type(),
                        preferences.domainParty(),
                        "可生成下一步行动",
                        match.materialIndex(), match.materialName(), daysForMaterialIndex(match.materialIndex())));
                continue;
            }

            Optional<CultivationMaterialSourceCatalog.BossSource> boss =
                    materialSourceCatalog.findBoss(entry.materialName());
            if (boss.isPresent()) {
                CultivationMaterialSourceCatalog.BossSource source = boss.get();
                bossActions.add(new CultivationExecutionProjection.BossAction(
                        entry.materialName(), entry.remaining(), source.bossName(), source.country(),
                        bossParty(autoPlanConfiguration, preferences), bossSettings(autoPlanConfiguration),
                        "待 AutoPlan 首领任务执行"));
                continue;
            }

            Optional<String> specialtyCountry = materialSourceCatalog.findSpecialtyCountry(entry.materialName());
            if (specialtyCountry.isPresent()) {
                String country = specialtyCountry.get();
                gatherTargets.add(new CultivationExecutionProjection.GatherTarget(
                        entry.materialName(), entry.required(), entry.baselineOwned(), entry.currentOwned(), entry.remaining(),
                        country, "selectLocalSpecialty_" + country));
                continue;
            }

            Optional<String> weeklyBoss = materialSourceCatalog.findWeeklyBoss(entry.materialName());
            if (weeklyBoss.isPresent()) {
                weeklyBossActions.add(new CultivationExecutionProjection.WeeklyBossAction(
                        entry.materialName(), entry.remaining(), weeklyBoss.get(),
                        weeklyBossSettings(weeklyBossConfiguration, weeklyBoss.get()),
                        weeklyBossState(weeklyBossConfiguration)));
                continue;
            }

            Optional<CultivationMaterialSourceCatalog.MonsterSource> monster =
                    materialSourceCatalog.findMonster(entry.materialName());
            if (monster.isPresent()) {
                CultivationMaterialSourceCatalog.MonsterSource source = monster.get();
                monsterTargets.add(new CultivationExecutionProjection.MonsterTarget(
                        entry.materialName(), entry.required(), entry.baselineOwned(), entry.currentOwned(), entry.remaining(),
                        source.routeFamily(), source.monsters()));
                continue;
            }

            String manualReason = MANUAL_MATERIALS.get(entry.materialName());
            if (manualReason != null) {
                pending.add(new CultivationExecutionProjection.PendingMaterial(
                        entry.materialName(), entry.remaining(), manualReason));
                continue;
            }

            if (observationService.isCraftable(entry.materialName())) {
                continue;
            }

            pending.add(new CultivationExecutionProjection.PendingMaterial(
                    entry.materialName(), entry.remaining(), "尚未接入可验证的自动执行适配器"));
        }

        CultivationExecutionProjection.GatherAction gatherAction = buildGatherAction(
                normalizedUid, preferences, gatherConfiguration, gatherTargets);
        CultivationExecutionProjection.MonsterAction monsterAction = buildMonsterAction(
                monsterConfiguration, monsterTargets);
        return new CultivationExecutionProjection(
                normalizedUid, revision.revision(), revision.state(), EXECUTION_MODE, craftingPlan.actions(),
                resinActions, bossActions, weeklyBossActions, gatherAction, monsterAction,
                pending, preferences, partyOptions(normalizedUid), combatStrategyOptions(normalizedUid),
                materialProgress(projectedRequirements));
    }

    public CultivationPlanRevisionResponse latestLedger(String uid) {
        String normalizedUid = requireUid(uid);
        return observationService.effective(planService.latest(normalizedUid));
    }

    public CultivationExecutionPreferences preferences(String uid) {
        String normalizedUid = requireUid(uid);
        CultivationModuleConfiguration autoPlan = configurationService.find(
                normalizedUid, AutoPlanResinExecutionModule.ID);
        CultivationModuleConfiguration gather = configurationService.find(
                normalizedUid, CdAwareAutoGatherExecutionModule.ID);
        return new CultivationExecutionPreferences(
                normalizedUid,
                setting(autoPlan, "partyName"),
                setting(gather, "partyName"),
                setting(gather, "partyName2nd"),
                gather.enabled());
    }

    public List<String> resinPriority(String uid) {
        CultivationModuleConfiguration configuration = configurationService.find(
                requireUid(uid), AutoPlanResinExecutionModule.ID);
        Object value = configuration.settings().get("resinPriority");
        if (!(value instanceof Iterable<?> values)) return List.of("浓缩树脂", "原粹树脂");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        values.forEach(item -> {
            String name = item == null ? "" : String.valueOf(item).trim();
            if (RESIN_SOURCES.contains(name)) selected.add(name);
        });
        return List.copyOf(selected);
    }

    public Map<String, List<String>> inventoryReconcileTargets(String uid) {
        CultivationPlanRevisionResponse ledger = latestLedger(uid);
        if (ledger == null) return Map.of();
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        grouped.put("Materials", new LinkedHashSet<>());
        grouped.put("CharacterDevelopmentItems", new LinkedHashSet<>());
        for (CultivationLedgerEntry entry : ledger.requirements()) {
            if (entry.remaining() <= 0) continue;
            if (CultivationExperienceBookFamily.FAMILY_NAME.equals(entry.materialName())) {
                CultivationExperienceBookFamily.TIERS.forEach(tier ->
                        grouped.get("CharacterDevelopmentItems").add(tier.materialName()));
                continue;
            }
            if (materialSourceCatalog.findSpecialtyCountry(entry.materialName()).isPresent()) {
                grouped.get("Materials").add(entry.materialName());
            } else {
                var family = observationService.craftingFamily(entry.materialName());
                if (family.isPresent()) {
                    family.get().tiers().forEach(tier ->
                            grouped.get("CharacterDevelopmentItems").add(tier.materialName()));
                } else if (materialSourceCatalog.findMonster(entry.materialName()).isPresent()) {
                    grouped.get("CharacterDevelopmentItems").add(entry.materialName());
                }
            }
        }
        grouped.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return grouped.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    public String craftingCountry(String uid) {
        CultivationModuleConfiguration configuration = configurationService.find(
                requireUid(uid), AutoPlanResinExecutionModule.ID);
        String country = setting(configuration, "craftingCountry");
        return country.isBlank() ? "枫丹" : country;
    }

    @Transactional(rollbackFor = Exception.class)
    public CultivationExecutionPreferences savePreferences(CultivationExecutionPreferences request) {
        String uid = requireUid(request.uid());
        CultivationModuleConfiguration currentAutoPlan = configurationService.find(
                uid, AutoPlanResinExecutionModule.ID);
        Map<String, Object> autoPlanSettings = new LinkedHashMap<>(currentAutoPlan.settings());
        autoPlanSettings.put("partyName", trim(request.domainParty()));
        configurationService.save(uid, AutoPlanResinExecutionModule.ID,
                new CultivationModuleConfigurationRequest(currentAutoPlan.enabled(), autoPlanSettings));
        CultivationModuleConfiguration currentGather = configurationService.find(
                uid, CdAwareAutoGatherExecutionModule.ID);
        Map<String, Object> gatherSettings = new LinkedHashMap<>(currentGather.settings());
        gatherSettings.put("partyName", trim(request.gatherParty()));
        gatherSettings.put("partyName2nd", trim(request.gatherFallbackParty()));
        configurationService.save(uid, CdAwareAutoGatherExecutionModule.ID,
                new CultivationModuleConfigurationRequest(request.gatherEnabled(), gatherSettings));
        return preferences(uid);
    }

    private List<String> partyOptions(String uid) {
        Set<String> names = new LinkedHashSet<>();
        BetterGiCombatOptionCatalog.Options installed = combatOptionCatalog.discover();
        installed.parties().forEach(name -> addParty(names, name));
        CultivationExecutionPreferences preferences = preferences(uid);
        addParty(names, preferences.domainParty());
        addParty(names, preferences.gatherParty());
        addParty(names, preferences.gatherFallbackParty());
        configurationService.findAll(uid).stream()
                .flatMap(configuration -> configuration.settings().entrySet().stream())
                .filter(entry -> entry.getKey().toLowerCase().contains("party"))
                .forEach(entry -> addOptionValue(names, entry.getValue()));
        autoPlanService.find(uid, null).stream()
                .map(config -> config.toVo())
                .forEach(plan -> collectParties(plan, names));
        CultivationModuleConfiguration group = configurationService.find(
                uid, ScriptGroupSettingsExecutionModule.ID);
        if (group != null) {
            addOptionValue(names, group.settings().get("managedPartyOptions"));
            removeOptionValue(names, group.settings().get("hiddenPartyOptions"));
        }
        return List.copyOf(names);
    }

    private List<String> combatStrategyOptions(String uid) {
        Set<String> names = new LinkedHashSet<>(combatOptionCatalog.discover().strategies());
        configurationService.findAll(uid).stream()
                .flatMap(configuration -> configuration.settings().entrySet().stream())
                .filter(entry -> entry.getKey().toLowerCase().contains("strategy"))
                .forEach(entry -> addOptionValue(names, entry.getValue()));
        CultivationModuleConfiguration group = configurationService.find(
                uid, ScriptGroupSettingsExecutionModule.ID);
        if (group != null) {
            addOptionValue(names, group.settings().get("managedCombatStrategyOptions"));
            removeOptionValue(names, group.settings().get("hiddenCombatStrategyOptions"));
        }
        names.add("根据队伍自动选择");
        List<String> result = new ArrayList<>();
        result.add("根据队伍自动选择");
        names.stream().filter(name -> !"根据队伍自动选择".equals(name)).sorted().forEach(result::add);
        return List.copyOf(result);
    }

    private List<CultivationExecutionProjection.MaterialProgress> materialProgress(
            List<CultivationLedgerEntry> entries) {
        return entries.stream().map(entry -> {
            var experienceTier = CultivationExperienceBookFamily.tier(entry.materialName());
            if (experienceTier.isPresent()) {
                var tier = experienceTier.get();
                return new CultivationExecutionProjection.MaterialProgress(
                        entry.materialName(), entry.currentOwned(), entry.required(), entry.remaining(),
                        CultivationExperienceBookFamily.FAMILY_NAME,
                        CultivationExperienceBookFamily.TIERS.indexOf(tier),
                        CultivationExperienceBookFamily.TIERS.size(),
                        tier.qualityLevel(), tier.experiencePerItem());
            }
            Optional<CultivationMaterialCraftingCatalog.CraftFamily> family =
                    observationService.craftingFamily(entry.materialName());
            if (family == null || family.isEmpty()) {
                return new CultivationExecutionProjection.MaterialProgress(
                        entry.materialName(), entry.currentOwned(), entry.required(), entry.remaining(),
                        entry.materialName(), 0, 1, 0, 0);
            }
            List<CultivationMaterialCraftingCatalog.CraftTier> tiers = family.get().tiers();
            for (int index = 0; index < tiers.size(); index++) {
                CultivationMaterialCraftingCatalog.CraftTier tier = tiers.get(index);
                if (tier.materialName().equals(entry.materialName())) {
                    return new CultivationExecutionProjection.MaterialProgress(
                            entry.materialName(), entry.currentOwned(), entry.required(), entry.remaining(),
                            family.get().familyName(), index, tiers.size(), tier.qualityLevel(), 0);
                }
            }
            return new CultivationExecutionProjection.MaterialProgress(
                    entry.materialName(), entry.currentOwned(), entry.required(), entry.remaining(),
                    family.get().familyName(), 0, tiers.size(), 0, 0);
        }).toList();
    }

    private static void collectParties(AutoPlan plan, Set<String> names) {
        if (plan.getAutoDomain() != null) addParty(names, plan.getAutoDomain().getPartyName());
        if (plan.getAutoLeyLineOutcrop() != null) {
            addParty(names, plan.getAutoLeyLineOutcrop().getTeam());
            addParty(names, plan.getAutoLeyLineOutcrop().getFriendshipTeam());
        }
        if (plan.getAutoStygianOnslaught() != null) {
            addParty(names, plan.getAutoStygianOnslaught().getFightTeamName());
        }
        if (plan.getAutoBoss() != null) addParty(names, plan.getAutoBoss().getTeamName());
    }

    private static void addOptionValue(Set<String> target, Object value) {
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> {
                if (item instanceof CharSequence text) addParty(target, text.toString());
            });
        } else if (value instanceof CharSequence text) {
            addParty(target, text.toString());
        }
    }

    private static void removeOptionValue(Set<String> target, Object value) {
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> {
                if (item != null) target.remove(String.valueOf(item).trim());
            });
        } else if (value != null) {
            target.remove(String.valueOf(value).trim());
        }
    }

    private static CultivationExecutionProjection.GatherAction buildGatherAction(
            String uid,
            CultivationExecutionPreferences preferences,
            CultivationModuleConfiguration configuration,
            List<CultivationExecutionProjection.GatherTarget> targets) {
        Map<String, Object> settings = new LinkedHashMap<>(configuration.settings());
        settings.put("runMode", "采集选中的材料");
        settings.put("partyName", preferences.gatherParty());
        settings.put("partyName2nd", preferences.gatherFallbackParty());
        settings.put("targetCountOfSelected", "csv");
        settings.put("manualSetAccountName", uid);

        Map<String, List<String>> selections = new LinkedHashMap<>();
        targets.forEach(target -> selections
                .computeIfAbsent(target.selectionKey(), ignored -> new ArrayList<>())
                .add(target.materialName()));
        settings.putAll(selections);

        String state;
        if (targets.isEmpty()) {
            state = "当前无地方特产缺口";
        } else {
            state = "待 CD-Aware-AutoGather 执行";
        }
        return new CultivationExecutionProjection.GatherAction(
                "CD-Aware-AutoGather", state, settings, List.copyOf(targets));
    }

    private CultivationExecutionProjection.MonsterAction buildMonsterAction(
            CultivationModuleConfiguration configuration,
            List<CultivationExecutionProjection.MonsterTarget> targets) {
        Map<String, Object> settings = new LinkedHashMap<>(configuration.settings());
        Set<String> routeFamilies = new LinkedHashSet<>();
        targets.stream().map(CultivationExecutionProjection.MonsterTarget::routeFamily)
                .forEach(routeFamilies::add);
        settings.put("routeFamilies", List.copyOf(routeFamilies));
        settings.put("key", "PGCSBY37NJA");
        settings.put("config_run", "执行");

        String state;
        if (targets.isEmpty()) {
            state = "当前无怪物材料缺口";
        } else {
            state = "待 FullyAutoAndSemiAutoTools 执行";
        }
        return new CultivationExecutionProjection.MonsterAction(
                "FullyAutoAndSemiAutoTools", state, settings, List.copyOf(targets),
                materialSourceCatalog.availableMonsterRouteFamilies());
    }

    private static String bossParty(CultivationModuleConfiguration configuration,
                                    CultivationExecutionPreferences preferences) {
        String party = setting(configuration, "bossPartyName");
        return StrUtil.isBlank(party) ? preferences.domainParty() : party;
    }

    private static Map<String, Object> bossSettings(CultivationModuleConfiguration configuration) {
        Map<String, Object> result = new LinkedHashMap<>();
        List.of("bossStrategyName", "bossReviveRetryCount", "bossReturnToStatueAfterEachRound",
                        "bossRewardRecognitionEnabled", "bossTimeoutSeconds")
                .forEach(key -> result.put(key, configuration.settings().get(key)));
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private static Map<String, Object> weeklyBossSettings(
            CultivationModuleConfiguration configuration, String bossName) {
        Map<String, Object> settings = new LinkedHashMap<>(configuration.settings());
        settings.put("monsterName", bossName);
        return settings;
    }

    private static String weeklyBossState(CultivationModuleConfiguration configuration) {
        if (!Boolean.TRUE.equals(configuration.settings().get("unfairContractTerms"))) {
            return "需确认周本脚本风险条款";
        }
        return "待 WeeklyBoss 单轮执行";
    }

    private static List<Integer> daysForMaterialIndex(int index) {
        return switch (index) {
            case 1 -> List.of(0, 1, 4);
            case 2 -> List.of(0, 2, 5);
            case 3 -> List.of(0, 3, 6);
            default -> List.of();
        };
    }

    private static Optional<DomainMatch> findDomain(String materialName,
                                                    List<Map<String, Object>> domains) {
        String comparableName = normalizeTalentMaterial(materialName);
        String materialFamily = materialFamily(materialName);
        for (Map<String, Object> domain : domains) {
            Object rewards = domain.get("list");
            if (!(rewards instanceof Collection<?> collection)) {
                continue;
            }
            List<String> rewardNames = collection.stream().map(String::valueOf).toList();
            for (int index = 0; index < rewardNames.size(); index++) {
                String item = rewardNames.get(index);
                if (item.equals(materialName)
                        || item.equals(comparableName)
                        || materialFamily.equals(materialFamily(item))) {
                    return Optional.of(new DomainMatch(
                            String.valueOf(domain.get("name")), String.valueOf(domain.get("type")),
                            index + 1, item));
                }
            }
        }
        return Optional.empty();
    }

    private static List<Map<String, Object>> effectiveDomains(List<Map<String, Object>> configured) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        defaultDomains().forEach(domain -> merged.put(String.valueOf(domain.get("name")), domain));
        if (configured != null) {
            configured.forEach(domain -> merged.put(String.valueOf(domain.get("name")), domain));
        }
        return List.copyOf(merged.values());
    }

    private static List<Map<String, Object>> defaultDomains() {
        return List.of(
                domain("无光的深都", "天赋", "「月光」的哲学", "「乐园」的哲学", "「浪迹」的哲学"),
                domain("蕴火的幽墟", "天赋", "「角逐」的哲学", "「焚燔」的哲学", "「纷争」的哲学"),
                domain("苍白的遗荣", "天赋", "「公平」的哲学", "「正义」的哲学", "「秩序」的哲学"),
                domain("昏识塔", "天赋", "「诤言」的哲学", "「巧思」的哲学", "「笃行」的哲学"),
                domain("董色之庭", "天赋", "「浮世」的哲学", "「风雅」的哲学", "「天光」的哲学"),
                domain("太山府", "天赋", "「繁荣」的哲学", "「勤劳」的哲学", "「黄金」的哲学"),
                domain("忘却之峡", "天赋", "「自由」的哲学", "「抗争」的哲学", "「诗文」的哲学"),
                domain("失落的月庭", "武器", "奇巧秘器的真愿", "长夜燧火的烈辉", "终北遗嗣的煌熠"),
                domain("深古瞭望所", "武器", "贡祭炽心的荣膺", "谚妄圣主的神面", "神合秘烟的启示"),
                domain("深潮的余响", "武器", "悠古弦音的回响", "纯圣露滴的真粹", "无垢之海的金杯"),
                domain("有顶塔", "武器", "谧林涓露的金符", "绿洲花园的真谛", "烈日威权的旧日"),
                domain("砂流之庭", "武器", "远海夷地的金枝", "鸣神御灵的勇武", "今昔剧画之鬼人"),
                domain("震雷连山密宫", "武器", "孤云寒林的神体", "雾海云间的转还", "漆黑陨铁的一块"),
                domain("塞西莉亚苗圃", "武器", "高塔孤王的碎梦", "凛风奔狼的怀乡", "狮牙斗士的理想"));
    }

    private static Map<String, Object> domain(String name, String type, String... rewards) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("type", type);
        result.put("list", List.of(rewards));
        return result;
    }

    private static String normalizeTalentMaterial(String materialName) {
        return materialName
                .replace("的教导", "的哲学")
                .replace("的指引", "的哲学");
    }

    private static String materialFamily(String materialName) {
        int separator = materialName.lastIndexOf('的');
        return separator > 0 ? materialName.substring(0, separator) : materialName;
    }

    private static void addParty(Set<String> names, String name) {
        if (StrUtil.isNotBlank(name)) names.add(name.trim());
    }

    private static String requireUid(String uid) {
        return CultivationUid.normalize(uid);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String setting(CultivationModuleConfiguration configuration, String key) {
        Object value = configuration.settings().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record DomainMatch(String name, String type, int materialIndex, String materialName) {
    }
}
