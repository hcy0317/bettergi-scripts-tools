package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.execution.module.AutoPlanResinExecutionModule;
import com.cloud_guest.cultivation.execution.module.CdAwareAutoGatherExecutionModule;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfiguration;
import com.cloud_guest.cultivation.execution.module.CultivationModuleConfigurationService;
import com.cloud_guest.cultivation.execution.module.CultivationModuleDefinition;
import com.cloud_guest.cultivation.execution.module.FullyAutoToolsExecutionModule;
import com.cloud_guest.cultivation.execution.module.WeeklyBossExecutionModule;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanApplicationService;
import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import com.cloud_guest.service.AutoPlanService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationExecutionServiceTest {
    @Test
    void expandsCraftableLedgerRowsToEveryFamilyTierForReconciliation() {
        CultivationPlanApplicationService planService = mock(CultivationPlanApplicationService.class);
        CultivationLedgerObservationService observationService = mock(CultivationLedgerObservationService.class);
        CultivationMaterialSourceCatalog materialSourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        CultivationPlanRevisionResponse ledger = new CultivationPlanRevisionResponse(
                1, "102550550", 4, "NEEDS_CRAFT", "name-only-v1", 2, "hash",
                "PP-OCRv6", "local", List.of(entry("「笃行」的哲学", 4, 0, 4)),
                LocalDateTime.now());
        when(planService.latest("102550550")).thenReturn(ledger);
        when(observationService.effective(ledger)).thenReturn(ledger);
        when(materialSourceCatalog.findMonster("「笃行」的哲学")).thenReturn(Optional.of(
                new CultivationMaterialSourceCatalog.MonsterSource(
                        "丘丘人", List.of("丘丘人"), List.of("丘丘人"))));
        when(observationService.craftingFamily("「笃行」的哲学")).thenReturn(Optional.of(
                new CultivationMaterialCraftingCatalog.CraftFamily(
                        "「笃行」的哲学",
                        List.of(
                                new CultivationMaterialCraftingCatalog.CraftTier(
                                        104335, "「笃行」的教导", "角色天赋素材", 2),
                                new CultivationMaterialCraftingCatalog.CraftTier(
                                        104336, "「笃行」的指引", "角色天赋素材", 3),
                                new CultivationMaterialCraftingCatalog.CraftTier(
                                        104337, "「笃行」的哲学", "角色天赋素材", 4)))));
        CultivationExecutionService service = new CultivationExecutionService(
                planService, observationService, mock(AutoPlanService.class),
                mock(CultivationModuleConfigurationService.class),
                materialSourceCatalog,
                mock(BetterGiCombatOptionCatalog.class));

        assertThat(service.inventoryReconcileTargets("102550550"))
                .containsEntry("CharacterDevelopmentItems", List.of(
                        "「笃行」的教导", "「笃行」的指引", "「笃行」的哲学"));
    }

    @Test
    void preservesAnExplicitlyEmptyResinSelection() {
        CultivationModuleConfigurationService configurationService =
                mock(CultivationModuleConfigurationService.class);
        when(configurationService.find("102550550", AutoPlanResinExecutionModule.ID))
                .thenReturn(configuration(
                        AutoPlanResinExecutionModule.ID,
                        true,
                        Map.of("resinPriority", List.of())));
        CultivationExecutionService service = new CultivationExecutionService(
                mock(CultivationPlanApplicationService.class),
                mock(CultivationLedgerObservationService.class),
                mock(AutoPlanService.class),
                configurationService,
                mock(CultivationMaterialSourceCatalog.class),
                mock(BetterGiCombatOptionCatalog.class));

        assertThat(service.resinPriority("102550550")).isEmpty();
    }

    @Test
    void returnsTheEffectiveLedgerWithTheLatestAuthoritativeOwnedCount() {
        CultivationPlanApplicationService planService = mock(CultivationPlanApplicationService.class);
        CultivationLedgerObservationService observationService = mock(CultivationLedgerObservationService.class);
        CultivationPlanRevisionResponse imported = revision("IMPORTED", 6, 6, 34);
        CultivationPlanRevisionResponse effective = revision("ACTIVE", 6, 18, 22);
        when(planService.latest("102550550")).thenReturn(imported);
        when(observationService.effective(imported)).thenReturn(effective);

        CultivationExecutionService service = new CultivationExecutionService(
                planService, observationService, mock(AutoPlanService.class),
                mock(CultivationModuleConfigurationService.class), mock(CultivationMaterialSourceCatalog.class),
                mock(BetterGiCombatOptionCatalog.class));

        CultivationPlanRevisionResponse result = service.latestLedger("102550550");

        assertThat(result.state()).isEqualTo("ACTIVE");
        assertThat(result.requirements()).singleElement().satisfies(entry -> {
            assertThat(entry.baselineOwned()).isEqualTo(6);
            assertThat(entry.currentOwned()).isEqualTo(18);
            assertThat(entry.remaining()).isEqualTo(22);
        });
    }

    @Test
    void projectsLedgerThroughResinGatherAndPendingAdapters() {
        CultivationPlanApplicationService planService = mock(CultivationPlanApplicationService.class);
        AutoPlanService autoPlanService = mock(AutoPlanService.class);
        CultivationModuleConfigurationService configurationService = mock(CultivationModuleConfigurationService.class);
        CultivationMaterialSourceCatalog materialSourceCatalog = mock(CultivationMaterialSourceCatalog.class);

        CultivationPlanRevisionResponse revision = new CultivationPlanRevisionResponse(
                1, "123456789", 3, "IMPORTED", "name-only-v1", 2, "hash",
                "PP-OCRv6", "local", List.of(
                entry("「浪迹」的指引", 63, 3, 60),
                entry("摩拉", 1_000_000, 250_000, 750_000),
                entry("沙脂蛹", 168, 4, 24, 144),
                entry("谜土的护符", 46, 1, 45),
                entry("史莱姆凝液", 18, 2, 16)), LocalDateTime.now());
        when(planService.latest("123456789")).thenReturn(revision);
        when(autoPlanService.findDomainAll()).thenReturn(List.of());
        when(autoPlanService.find("123456789", null)).thenReturn(List.of());

        CultivationModuleConfiguration autoPlan = configuration(
                AutoPlanResinExecutionModule.ID, true, Map.of("partyName", "速通"));
        CultivationModuleConfiguration gather = configuration(
                CdAwareAutoGatherExecutionModule.ID, true,
                Map.of("partyName", "钟纳久万", "partyName2nd", "钟纳久万"));
        CultivationModuleConfiguration monster = configuration(
                FullyAutoToolsExecutionModule.ID, true, Map.of(
                        "routeFamilies", List.of("巡陆艇"),
                        "visitStatueBeforeSwitchParty", false));
        CultivationModuleConfiguration weekly = configuration(
                WeeklyBossExecutionModule.ID, true, Map.of("unfairContractTerms", true));
        when(configurationService.find("123456789", AutoPlanResinExecutionModule.ID)).thenReturn(autoPlan);
        when(configurationService.find("123456789", CdAwareAutoGatherExecutionModule.ID)).thenReturn(gather);
        when(configurationService.find("123456789", FullyAutoToolsExecutionModule.ID)).thenReturn(monster);
        when(configurationService.find("123456789", WeeklyBossExecutionModule.ID)).thenReturn(weekly);
        when(configurationService.findAll(anyString())).thenReturn(List.of(autoPlan, gather, monster, weekly));
        when(materialSourceCatalog.findBoss(anyString())).thenReturn(Optional.empty());
        when(materialSourceCatalog.findSpecialtyCountry(anyString())).thenReturn(Optional.empty());
        when(materialSourceCatalog.findSpecialtyCountry("沙脂蛹")).thenReturn(Optional.of("须弥"));
        when(materialSourceCatalog.findMonster(anyString())).thenReturn(Optional.empty());
        when(materialSourceCatalog.findWeeklyBoss(anyString())).thenReturn(Optional.empty());
        when(materialSourceCatalog.findBoss("谜土的护符")).thenReturn(Optional.of(
                new CultivationMaterialSourceCatalog.BossSource("灵觉隐修的迷者", "纳塔")));
        when(materialSourceCatalog.findMonster("史莱姆凝液")).thenReturn(Optional.of(
                new CultivationMaterialSourceCatalog.MonsterSource(
                        "史莱姆", List.of("火史莱姆"), List.of("史莱姆"))));
        when(materialSourceCatalog.availableMonsterRouteFamilies()).thenReturn(List.of("史莱姆"));

        CultivationLedgerObservationService observationService = mock(CultivationLedgerObservationService.class);
        when(observationService.effective(revision)).thenReturn(revision);
        BetterGiCombatOptionCatalog optionCatalog = mock(BetterGiCombatOptionCatalog.class);
        when(optionCatalog.discover()).thenReturn(new BetterGiCombatOptionCatalog.Options(List.of(), List.of()));
        CultivationExecutionProjection result = new CultivationExecutionService(
                planService, observationService, autoPlanService, configurationService, materialSourceCatalog,
                optionCatalog)
                .projection("123456789");

        assertThat(result.resinActions()).extracting(CultivationExecutionProjection.ResinAction::sourceName)
                .containsExactly("无光的深都", "藏金之花");
        assertThat(result.gatherAction().settings())
                .containsEntry("targetCountOfSelected", "csv")
                .containsEntry("manualSetAccountName", "123456789")
                .containsEntry("selectLocalSpecialty_须弥", List.of("沙脂蛹"));
        assertThat(result.gatherAction().csvTargets()).singleElement()
                .satisfies(target -> {
                    assertThat(target.required()).isEqualTo(168L);
                    assertThat(target.baselineOwned()).isEqualTo(4L);
                    assertThat(target.currentOwned()).isEqualTo(24L);
                    assertThat(target.remaining()).isEqualTo(144L);
                });
        assertThat(result.bossActions()).singleElement()
                .extracting(CultivationExecutionProjection.BossAction::bossName)
                .isEqualTo("灵觉隐修的迷者");
        assertThat(result.monsterAction().targets()).singleElement()
                .extracting(CultivationExecutionProjection.MonsterTarget::routeFamily)
                .isEqualTo("史莱姆");
        assertThat(result.monsterAction().settings()).containsEntry("routeFamilies", List.of("史莱姆"));
        assertThat(result.pendingMaterials()).isEmpty();
        assertThat(result.partyOptions()).doesNotContain("false");
        assertThat(result.materialProgress())
                .filteredOn(progress -> progress.materialName().equals("沙脂蛹"))
                .singleElement()
                .satisfies(progress -> {
                    assertThat(progress.currentOwned()).isEqualTo(24L);
                    assertThat(progress.required()).isEqualTo(168L);
                    assertThat(progress.remaining()).isEqualTo(144L);
                });
    }

    @Test
    void craftingAdjustedRemainderRemovesTheStaleMonsterTarget() {
        CultivationPlanApplicationService planService = mock(CultivationPlanApplicationService.class);
        CultivationLedgerObservationService observationService = mock(CultivationLedgerObservationService.class);
        AutoPlanService autoPlanService = mock(AutoPlanService.class);
        CultivationModuleConfigurationService configurationService = mock(CultivationModuleConfigurationService.class);
        CultivationMaterialSourceCatalog materialSourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        BetterGiCombatOptionCatalog optionCatalog = mock(BetterGiCombatOptionCatalog.class);
        CultivationLedgerEntry target = entry("史莱姆清", 1, 0, 1);
        CultivationPlanRevisionResponse revision = new CultivationPlanRevisionResponse(
                1, "102550550", 5, "IMPORTED", "name-only-v1", 2, "hash",
                "PP-OCRv6", "local", List.of(target), LocalDateTime.now());
        CultivationModuleConfiguration enabled = configuration(
                AutoPlanResinExecutionModule.ID, true, Map.of());
        when(planService.latest("102550550")).thenReturn(revision);
        when(observationService.effective(revision)).thenReturn(revision);
        when(observationService.craftingPlan(revision.requirements())).thenReturn(
                new CultivationMaterialCraftingPlan(
                        Map.of("史莱姆清", 0L),
                        List.of(new CultivationCraftingAction("史莱姆清", 1, "史莱姆"))));
        when(configurationService.find(anyString(), anyString())).thenReturn(enabled);
        when(configurationService.findAll("102550550")).thenReturn(List.of(enabled));
        when(autoPlanService.findDomainAll()).thenReturn(List.of());
        when(autoPlanService.find("102550550", null)).thenReturn(List.of());
        when(optionCatalog.discover()).thenReturn(
                new BetterGiCombatOptionCatalog.Options(List.of(), List.of()));
        when(materialSourceCatalog.findMonster("史莱姆清")).thenReturn(Optional.of(
                new CultivationMaterialSourceCatalog.MonsterSource(
                        "史莱姆", List.of("大型水史莱姆"), List.of("史莱姆"))));

        CultivationExecutionProjection result = new CultivationExecutionService(
                planService, observationService, autoPlanService, configurationService,
                materialSourceCatalog, optionCatalog).projection("102550550");

        assertThat(result.craftingActions()).hasSize(1);
        assertThat(result.monsterAction().targets()).isEmpty();
        assertThat(result.materialProgress()).singleElement()
                .satisfies(progress -> assertThat(progress.remaining()).isZero());
    }

    private static CultivationLedgerEntry entry(String name, long required, long owned, long remaining) {
        return new CultivationLedgerEntry(
                null, name, required, owned, remaining, RemainingEvidence.OCR,
                0.99, false, List.of());
    }

    private static CultivationLedgerEntry entry(String name,
                                                 long required,
                                                 long baselineOwned,
                                                 long currentOwned,
                                                 long remaining) {
        return new CultivationLedgerEntry(
                null, name, required, baselineOwned, currentOwned, remaining,
                RemainingEvidence.OCR, 0.99, false, List.of());
    }

    private static CultivationPlanRevisionResponse revision(String state,
                                                            long baselineOwned,
                                                            long currentOwned,
                                                            long remaining) {
        return new CultivationPlanRevisionResponse(
                1, "102550550", 1, state, "name-only-v1", 2, "hash",
                "PP-OCRv6", "local", List.of(new CultivationLedgerEntry(
                null, "蕈王钩喙", 40, baselineOwned, currentOwned, remaining,
                RemainingEvidence.OCR, 0.99, false, List.of())), LocalDateTime.now());
    }

    private static CultivationModuleConfiguration configuration(String id,
                                                                boolean enabled,
                                                                Map<String, Object> settings) {
        return new CultivationModuleConfiguration(
                new CultivationModuleDefinition(id, id, "1.0", "", "", List.of(), List.of()),
                enabled, settings);
    }
}
