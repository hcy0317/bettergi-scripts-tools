package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationMaterialCraftingPlannerTest {
    @TempDir
    Path betterGiRoot;

    @Test
    void discoversConsecutiveCraftingTiersFromTheInstalledItemCatalog() throws Exception {
        CultivationMaterialCraftingCatalog catalog = catalog();

        assertThat(catalog.family("哀叙冰玉").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialName)
                .containsExactly("哀叙冰玉碎屑", "哀叙冰玉断片", "哀叙冰玉块", "哀叙冰玉");
        assertThat(catalog.family("「笃行」的指引").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                .containsExactly(2, 3, 4);
        assertThat(catalog.family("「慈爱」的哲学").orElseThrow().tiers())
                .extracting(
                        CultivationMaterialCraftingCatalog.CraftTier::materialName,
                        CultivationMaterialCraftingCatalog.CraftTier::materialType,
                        CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("「慈爱」的教导", "角色天赋素材", 2),
                        org.assertj.core.groups.Tuple.tuple("「慈爱」的指引", "角色天赋素材", 3),
                        org.assertj.core.groups.Tuple.tuple("「慈爱」的哲学", "角色天赋素材", 4));
        assertThat(catalog.family("幻造晶鳞石").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialName)
                .containsExactly("幻造萤屑", "幻造裂晶", "幻造晶鳞石");
        assertThat(catalog.family("智识之冕")).isEmpty();
        assertThat(catalog.family("沙脂蛹")).isEmpty();
    }

    @Test
    void discoversTheExistingFourStarMonsterMaterialTier() throws Exception {
        CultivationMaterialCraftingCatalog catalog = catalog();

        assertThat(catalog.family("黑晶号角")).isPresent();
        assertThat(catalog.family("黑晶号角").orElseThrow().tiers())
                .extracting(
                        CultivationMaterialCraftingCatalog.CraftTier::materialId,
                        CultivationMaterialCraftingCatalog.CraftTier::materialName,
                        CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(112014, "沉重号角", 2),
                        org.assertj.core.groups.Tuple.tuple(112015, "黑铜号角", 3),
                        org.assertj.core.groups.Tuple.tuple(112016, "黑晶号角", 4));
    }

    @Test
    void discoversOfficialSnowTiersWithDeclaredOrInferredHighestType() throws Exception {
        for (String highestType : List.of("角色与武器培养素材", "")) {
            CultivationMaterialCraftingCatalog catalog = catalogWithRows("""
                    material:112158,material:112158,雪结片心,角色与武器培养素材,,2,,,x
                    material:112159,material:112159,雪结镜心,角色与武器培养素材,,3,,,x
                    material:112160,material:112160,雪结无明心,%s,,4,,,x
                    """.formatted(highestType));

            var family = catalog.family("雪结无明心").orElseThrow();
            assertThat(family.tiers())
                    .extracting(
                            CultivationMaterialCraftingCatalog.CraftTier::materialId,
                            CultivationMaterialCraftingCatalog.CraftTier::materialName,
                            CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(112158, "雪结片心", 2),
                            org.assertj.core.groups.Tuple.tuple(112159, "雪结镜心", 3),
                            org.assertj.core.groups.Tuple.tuple(112160, "雪结无明心", 4));
            assertThat(catalog.family("雪结片心")).contains(family);
            assertThat(catalog.family("雪结镜心")).contains(family);
        }
    }

    @Test
    void keepsAdjacentCommonAndEliteFamiliesSeparateWhenQualityResets() throws Exception {
        CultivationMaterialCraftingCatalog catalog = catalogWithRows("""
                material:112011,material:112011,牢固的箭簇,角色与武器培养素材,,1,,,x
                material:112012,material:112012,锐利的箭簇,角色与武器培养素材,,2,,,x
                material:112013,material:112013,历战的箭簇,角色与武器培养素材,,3,,,x
                material:112014,material:112014,沉重号角,角色与武器培养素材,,2,,,x
                material:112015,material:112015,黑铜号角,角色与武器培养素材,,3,,,x
                material:112016,material:112016,黑晶号角,角色与武器培养素材,,4,,,x
                """);

        assertThat(catalog.family("历战的箭簇").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialId)
                .containsExactly(112011, 112012, 112013);
        assertThat(catalog.family("黑晶号角").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialId)
                .containsExactly(112014, 112015, 112016);
    }

    @Test
    void preservesFiveStarCharacterAndWeaponMaterialFamilies() throws Exception {
        CultivationMaterialCraftingCatalog catalog = catalog();

        assertThat(catalog.family("哀叙冰玉").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                .containsExactly(2, 3, 4, 5);
        assertThat(catalog.family("高塔孤王的碎梦").orElseThrow().tiers())
                .extracting(
                        CultivationMaterialCraftingCatalog.CraftTier::materialId,
                        CultivationMaterialCraftingCatalog.CraftTier::qualityLevel)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(114001, 2),
                        org.assertj.core.groups.Tuple.tuple(114002, 3),
                        org.assertj.core.groups.Tuple.tuple(114003, 4),
                        org.assertj.core.groups.Tuple.tuple(114004, 5));
    }

    @Test
    void rejectsMonsterQualityFiveAndDoesNotBridgeMissingIdsOrDifferentTypes() throws Exception {
        CultivationMaterialCraftingCatalog catalog = catalogWithRows("""
                material:900001,material:900001,上界二阶,角色与武器培养素材,,2,,,x
                material:900002,material:900002,上界三阶,角色与武器培养素材,,3,,,x
                material:900003,material:900003,上界四阶,角色与武器培养素材,,4,,,x
                material:900004,material:900004,不合法五阶,角色与武器培养素材,,5,,,x
                material:900010,material:900010,断档二阶,角色与武器培养素材,,2,,,x
                material:900012,material:900012,断档三阶,角色与武器培养素材,,3,,,x
                material:900020,material:900020,同类二阶,角色与武器培养素材,,2,,,x
                material:900021,material:900021,同类三阶,角色与武器培养素材,,3,,,x
                material:900022,material:900022,异类四阶,角色天赋素材,,4,,,x
                """);

        assertThat(catalog.family("上界四阶").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialId)
                .containsExactly(900001, 900002, 900003);
        assertThat(catalog.family("不合法五阶")).isEmpty();
        assertThat(catalog.family("断档二阶")).isEmpty();
        assertThat(catalog.family("断档三阶")).isEmpty();
        assertThat(catalog.family("同类三阶").orElseThrow().tiers())
                .extracting(CultivationMaterialCraftingCatalog.CraftTier::materialId)
                .containsExactly(900020, 900021);
        assertThat(catalog.family("异类四阶")).isEmpty();
    }

    @Test
    void plansExistingFourStarMonsterMaterialsFromThreeOwnedLowerTierItems() throws Exception {
        CultivationMaterialCraftingPlanner planner = new CultivationMaterialCraftingPlanner(catalog());

        CultivationMaterialCraftingPlan result = planner.plan(List.of(
                entry("沉重号角", 0, 0),
                entry("黑铜号角", 0, 3),
                entry("黑晶号角", 1, 0)));

        assertThat(result.actions())
                .extracting(CultivationCraftingAction::materialName, CultivationCraftingAction::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("黑晶号角", 1L));
        assertThat(result.remainingByMaterial()).containsEntry("黑晶号角", 0L);
        assertThat(result.needsCraft()).isTrue();
    }

    @Test
    void retriesCatalogLoadingAfterTheDetectedBetterGiRootBecomesReady() throws Exception {
        Path unavailableRoot = betterGiRoot.resolve("not-ready");
        Path readyRoot = betterGiRoot.resolve("ready");
        writeCatalog(readyRoot);
        CultivationMaterialSourceCatalog sourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        when(sourceCatalog.betterGiRoot()).thenReturn(unavailableRoot, readyRoot);
        CultivationMaterialCraftingCatalog catalog = new CultivationMaterialCraftingCatalog(sourceCatalog);

        assertThat(catalog.family("哀叙冰玉")).isEmpty();
        assertThat(catalog.family("哀叙冰玉")).isPresent();
    }

    @Test
    void unavailableBetterGiRootDoesNotBreakNonCraftingProjections() throws Exception {
        Path readyRoot = betterGiRoot.resolve("ready-after-discovery");
        writeCatalog(readyRoot);
        CultivationMaterialSourceCatalog sourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        when(sourceCatalog.betterGiRoot())
                .thenThrow(new IllegalStateException("BetterGI unavailable"))
                .thenReturn(readyRoot);
        CultivationMaterialCraftingCatalog catalog = new CultivationMaterialCraftingCatalog(sourceCatalog);

        assertThat(catalog.family("哀叙冰玉")).isEmpty();
        assertThat(catalog.family("哀叙冰玉")).isPresent();
    }

    @Test
    void reservesEveryTierRequirementBeforePlanningThreeToOneCrafts() throws Exception {
        CultivationMaterialCraftingPlanner planner = new CultivationMaterialCraftingPlanner(catalog());

        CultivationMaterialCraftingPlan result = planner.plan(List.of(
                entry("「笃行」的教导", 9, 18),
                entry("「笃行」的指引", 6, 6),
                entry("「笃行」的哲学", 4, 2)));

        assertThat(result.actions())
                .extracting(CultivationCraftingAction::materialName, CultivationCraftingAction::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("「笃行」的指引", 3L),
                        org.assertj.core.groups.Tuple.tuple("「笃行」的哲学", 1L));
        assertThat(result.remainingByMaterial())
                .containsEntry("「笃行」的教导", 0L)
                .containsEntry("「笃行」的指引", 0L)
                .containsEntry("「笃行」的哲学", 1L);
        assertThat(result.needsCraft()).isTrue();
    }

    @Test
    void plansTheCompleteGemCraftingChainWithoutRoundingPartialMaterialsUp() throws Exception {
        CultivationMaterialCraftingPlanner planner = new CultivationMaterialCraftingPlanner(catalog());

        CultivationMaterialCraftingPlan result = planner.plan(List.of(
                entry("哀叙冰玉碎屑", 0, 27),
                entry("哀叙冰玉断片", 0, 0),
                entry("哀叙冰玉块", 0, 0),
                entry("哀叙冰玉", 1, 0)));

        assertThat(result.actions())
                .extracting(CultivationCraftingAction::materialName, CultivationCraftingAction::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("哀叙冰玉断片", 9L),
                        org.assertj.core.groups.Tuple.tuple("哀叙冰玉块", 3L),
                        org.assertj.core.groups.Tuple.tuple("哀叙冰玉", 1L));
        assertThat(result.remainingByMaterial()).containsEntry("哀叙冰玉", 0L);
    }

    @Test
    void doesNotInflateAHighWaterDeficitAfterInventoryConsumption() throws Exception {
        CultivationMaterialCraftingPlanner planner = new CultivationMaterialCraftingPlanner(catalog());
        CultivationLedgerEntry teachings = entry("「笃行」的教导", 0, 0);
        CultivationLedgerEntry guides = entry("「笃行」的指引", 0, 6);
        CultivationLedgerEntry philosophies = new CultivationLedgerEntry(
                null, "「笃行」的哲学", 92, 90, 6L, 2,
                RemainingEvidence.OCR, 1.0, false, List.of());

        CultivationMaterialCraftingPlan result = planner.plan(
                List.of(teachings, guides, philosophies));

        assertThat(result.actions())
                .extracting(CultivationCraftingAction::materialName, CultivationCraftingAction::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("「笃行」的哲学", 2L));
        assertThat(result.remainingByMaterial()).containsEntry("「笃行」的哲学", 0L);
    }

    private CultivationMaterialCraftingCatalog catalog() throws Exception {
        writeCatalog(betterGiRoot);
        CultivationMaterialSourceCatalog sourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        when(sourceCatalog.betterGiRoot()).thenReturn(betterGiRoot);
        return new CultivationMaterialCraftingCatalog(sourceCatalog);
    }

    private CultivationMaterialCraftingCatalog catalogWithRows(String rows) throws Exception {
        Path csv = betterGiRoot.resolve(Path.of("Assets", "Model", "ItemV2", "item.csv"));
        Files.createDirectories(csv.getParent());
        Files.writeString(csv,
                "variant_id,item_class_id,item_name,material_type,food_base_name,quality_level,weapon_state,weapon_type,embedding\n" + rows);
        CultivationMaterialSourceCatalog sourceCatalog = mock(CultivationMaterialSourceCatalog.class);
        when(sourceCatalog.betterGiRoot()).thenReturn(betterGiRoot);
        return new CultivationMaterialCraftingCatalog(sourceCatalog);
    }

    private static void writeCatalog(Path root) throws Exception {
        Path csv = root.resolve(Path.of("Assets", "Model", "ItemV2", "item.csv"));
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, """
                variant_id,item_class_id,item_name,material_type,food_base_name,quality_level,weapon_state,weapon_type,embedding
                material:104161,material:104161,哀叙冰玉碎屑,角色突破素材,,2,,,x
                material:104162,material:104162,哀叙冰玉断片,角色突破素材,,3,,,x
                material:104163,material:104163,哀叙冰玉块,角色突破素材,,4,,,x
                material:104164,material:104164,哀叙冰玉,角色突破素材,,5,,,x
                material:104335,material:104335,「笃行」的教导,角色天赋素材,,2,,,x
                material:104336,material:104336,「笃行」的指引,角色天赋素材,,3,,,x
                material:104337,material:104337,「笃行」的哲学,角色天赋素材,,4,,,x
                material:104338,material:104338,智识之冕,角色天赋素材,,5,,,x
                material:104365,material:104365,「慈爱」的教导,角色天赋素材,,2,,,x
                material:104366,material:104366,「慈爱」的指引,角色天赋素材,,3,,,x
                material:104367,material:104367,「慈爱」的哲学,,,4,,,x
                material:112014,material:112014,沉重号角,角色与武器培养素材,,2,,,x
                material:112015,material:112015,黑铜号角,角色与武器培养素材,,3,,,x
                material:112016,material:112016,黑晶号角,角色与武器培养素材,,4,,,x
                material:112080,material:112080,异海凝珠,角色与武器培养素材,,1,,,x
                material:112081,material:112081,异海之块,角色与武器培养素材,,2,,,x
                material:112082,material:112082,异色结晶石,角色与武器培养素材,,3,,,x
                material:112146,material:112146,幻造萤屑,角色与武器培养素材,,1,,,x
                material:112147,material:112147,幻造裂晶,角色与武器培养素材,,2,,,x
                material:112148,material:112148,幻造晶鳞石,,,3,,,x
                material:114001,material:114001,高塔孤王的破瓦,武器突破素材,,2,,,x
                material:114002,material:114002,高塔孤王的残垣,武器突破素材,,3,,,x
                material:114003,material:114003,高塔孤王的断片,武器突破素材,,4,,,x
                material:114004,material:114004,高塔孤王的碎梦,,,5,,,x
                material:101222,material:101222,沙脂蛹,角色突破素材,,0,,,x
                """);
    }

    private static CultivationLedgerEntry entry(String name, long required, long owned) {
        return new CultivationLedgerEntry(
                null, name, required, owned, owned, Math.max(required - owned, 0),
                RemainingEvidence.OCR, 1.0, false, List.of());
    }
}
