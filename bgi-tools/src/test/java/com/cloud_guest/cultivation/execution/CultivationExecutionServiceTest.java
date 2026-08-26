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
