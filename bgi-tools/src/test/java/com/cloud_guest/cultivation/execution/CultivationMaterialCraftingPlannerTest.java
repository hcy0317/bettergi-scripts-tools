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
        assertThat(catalog.family("沙脂蛹")).isEmpty();
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

    private CultivationMaterialCraftingCatalog catalog() throws Exception {
        writeCatalog(betterGiRoot);
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
                material:112080,material:112080,异海凝珠,角色与武器培养素材,,1,,,x
                material:112081,material:112081,异海之块,角色与武器培养素材,,2,,,x
                material:112082,material:112082,异色结晶石,角色与武器培养素材,,3,,,x
                material:101222,material:101222,沙脂蛹,角色突破素材,,0,,,x
                """);
    }

    private static CultivationLedgerEntry entry(String name, long required, long owned) {
        return new CultivationLedgerEntry(
                null, name, required, owned, owned, Math.max(required - owned, 0),
                RemainingEvidence.OCR, 1.0, false, List.of());
    }
}
