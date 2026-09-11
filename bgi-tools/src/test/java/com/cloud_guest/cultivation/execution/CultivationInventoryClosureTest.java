package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.ocr.CultivationOcrProperties;
import com.cloud_guest.cultivation.ocr.RemainingEvidence;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionEntity;
import com.cloud_guest.cultivation.persistence.CultivationExecutionActionMapper;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionEntity;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionMapper;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigMapper;
import com.cloud_guest.cultivation.execution.module.*;
import com.cloud_guest.service.AutoPlanService;
import com.cloud_guest.cultivation.plan.CultivationLedgerEntry;
import com.cloud_guest.cultivation.plan.CultivationPlanApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.ibatis.session.SqlSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationInventoryClosureTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private SqlSession database;
    private CultivationExecutionActionMapper actions;
    private int batchSequence;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void openIsolatedDatabase() throws Exception {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(configuration)
                .setMetaObjectHandler(new com.cloud_guest.mp.abs.handler.AbsEntityHandler() { });
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
                        "org.sqlite.JDBC", "jdbc:sqlite::memory:", null, null)));
        configuration.addMapper(CultivationExecutionActionMapper.class);
        configuration.addMapper(CultivationModuleConfigMapper.class);
        database = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder()
                .build(configuration).openSession(true);
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(database.getConnection(),
                new org.springframework.core.io.ClassPathResource("sql/sqlite.sql"));
        actions = database.getMapper(CultivationExecutionActionMapper.class);
    }

    @AfterEach
    void closeDatabase() { if (database != null) database.close(); }

    @Test
    void completedMaterialThatLaterDecreasedRemainsReachableByInventoryReconciliation() throws Exception {
        var service = service();
        var before = batch("before", Map.of("哀叙冰玉", 12L, "哀叙冰玉块", 18L));
        var decrease = batch("decrease", Map.of("哀叙冰玉", 3L, "哀叙冰玉块", 11L));
        actions.insert(before);
        actions.insert(decrease);

        var ledger = service.latestLedger("102550550");
        assertThat(ledger.state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(ledger.requirements()).allSatisfy(entry -> assertThat(entry.remaining()).isZero());
        assertThat(service.inventoryReconcileTargets("102550550"))
                .containsOnlyKeys("CharacterDevelopmentItems")
                .containsEntry("CharacterDevelopmentItems", List.of(
                        "哀叙冰玉碎屑", "哀叙冰玉断片", "哀叙冰玉块", "哀叙冰玉"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cachedPositiveStockCannotConfirmAnUnknownOrOmittedDecrease(boolean explicitlyUnknown) throws Exception {
        var service = service();
        actions.insert(batch("before", Map.of("哀叙冰玉", 12L, "哀叙冰玉块", 18L)));
        actions.insert(batch("decrease", Map.of("哀叙冰玉", 3L, "哀叙冰玉块", 11L)));
        var driver = new CultivationPlanDrivenExecutionService(service, actions, json, CLOCK);
        var claim = driver.claimInventoryReconcile("102550550", "inventory");
        var observed = new LinkedHashMap<>(Map.of("哀叙冰玉碎屑", 0L, "哀叙冰玉断片", 0L, "哀叙冰玉块", 11L));
        if (explicitlyUnknown) observed.put("哀叙冰玉", -1L);

        var result = driver.recordInventoryObservations("102550550", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 2, claim.actionId() + ":result", observed));
        assertThat(result.status()).isEqualTo("NEEDS_RECONCILE");
        assertThat(service.latestLedger("102550550").state()).isEqualTo("NEEDS_RECONCILE");
        assertThat(service.pendingInventoryReconciliationMaterials("102550550", 2)).contains("哀叙冰玉");
        var stillUnknown = driver.recordInventoryObservations("102550550", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 2, claim.actionId() + ":result", observed));
        assertThat(stillUnknown.status()).isEqualTo("NEEDS_RECONCILE");
        observed.put("哀叙冰玉", 3L);
        var confirmed = driver.recordInventoryObservations("102550550", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 2, claim.actionId() + ":result", observed));
        assertThat(confirmed.status()).isEqualTo("REPLANNING");
        assertThat(service.pendingInventoryReconciliationMaterials("102550550", 2)).isEmpty();
        assertThat(driver.claim("102550550", "next-executor").status()).isEqualTo("COMPLETED");
        assertThat(service.latestLedger("102550550").requirements())
                .filteredOn(entry -> "哀叙冰玉".equals(entry.materialName()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.baselineOwned()).isEqualTo(9);
                    assertThat(entry.currentOwned()).isEqualTo(3);
                    assertThat(entry.remaining()).isZero();
                });
    }

    @Test
    void anotherDecreaseRequiresFreshConfirmationAndWrongOwnersCannotSupplyIt() throws Exception {
        var service = service();
        actions.insert(batch("before", Map.of("哀叙冰玉", 12L, "哀叙冰玉块", 18L)));
        actions.insert(batch("decrease", Map.of("哀叙冰玉", 3L, "哀叙冰玉块", 11L)));
        var driver = new CultivationPlanDrivenExecutionService(service, actions, json, CLOCK);
        var claim = driver.claimInventoryReconcile("102550550", "inventory");
        var observed = Map.of("哀叙冰玉碎屑", 0L, "哀叙冰玉断片", 0L, "哀叙冰玉块", 11L, "哀叙冰玉", 2L);
        assertThatThrownBy(() -> driver.recordInventoryObservations("102550551", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 2, claim.actionId() + ":result", observed)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> driver.recordInventoryObservations("102550550", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 3, claim.actionId() + ":result", observed)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(driver.claimInventoryReconcile("102550550", "other-executor").status()).isEqualTo("BUSY");
        driver.recordInventoryObservations("102550550", new CultivationInventoryObservationRequest(
                claim.actionId(), "inventory", 2, claim.actionId() + ":result", observed));
        assertThat(driver.claim("102550550", "next-executor").status()).isEqualTo("PLAN_NEEDS_RECONCILE");
        assertThat(service.inventoryReconcileTargets("102550550").get("CharacterDevelopmentItems"))
                .contains("哀叙冰玉");
    }

    private CultivationExecutionService service() throws Exception {
        var csv = root.resolve("Assets/Model/ItemV2/item.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, """
                item_class_id,item_name,material_type,quality_level
                material:104161,哀叙冰玉碎屑,角色突破素材,2
                material:104162,哀叙冰玉断片,角色突破素材,3
                material:104163,哀叙冰玉块,角色突破素材,4
                material:104164,哀叙冰玉,角色突破素材,5
                material:104141,燃愿玛瑙碎屑,角色突破素材,2
                material:104142,燃愿玛瑙断片,角色突破素材,3
                material:104143,燃愿玛瑙块,角色突破素材,4
                material:104144,燃愿玛瑙,角色突破素材,5
                """);
        var properties = new CultivationOcrProperties();
        properties.setBettergiRoot(root.toString());
        var sources = new CultivationMaterialSourceCatalog(properties, json);
        var planner = new CultivationMaterialCraftingPlanner(new CultivationMaterialCraftingCatalog(sources));
        var observations = new CultivationLedgerObservationService(actions, json, planner);
        var revisions = mock(CultivationPlanRevisionMapper.class);
        var revision = new CultivationPlanRevisionEntity();
        revision.setId(1L);
        revision.setPreviewId(1L);
        revision.setUid("102550550");
        revision.setRevision(2);
        revision.setState("IMPORTED");
        revision.setRequirementsJson(json.writeValueAsString(List.of(
                entry("哀叙冰玉", 12, 9, 3), entry("哀叙冰玉块", 18, 18, 0),
                entry("燃愿玛瑙", 6, 6, 0))));
        when(revisions.findLatest("102550550")).thenReturn(revision);
        var plans = new CultivationPlanApplicationService(null, null, null, null, revisions, json);
        var registry = new CultivationModuleRegistry(List.of(new AutoPlanResinExecutionModule(),
                new CdAwareAutoGatherExecutionModule(), new FullyAutoToolsExecutionModule(),
                new WeeklyBossExecutionModule(), new ScriptGroupSettingsExecutionModule()));
        var configs = new CultivationModuleConfigurationService(registry,
                database.getMapper(CultivationModuleConfigMapper.class), json,
                new BetterGiInstalledScriptSettingsReader(sources, json));
        // AutoPlanService 是既有数据库/目录读取接口；账本、目标、回写业务均使用真实实现。
        var autoPlans = mock(AutoPlanService.class);
        when(autoPlans.findDomainAll()).thenReturn(List.of());
        when(autoPlans.find("102550550", null)).thenReturn(List.of());
        return new CultivationExecutionService(plans, observations, autoPlans, configs, sources,
                new BetterGiCombatOptionCatalog(sources, json));
    }

    private CultivationExecutionActionEntity batch(String id, Map<String, Long> owned) throws Exception {
        var batch = new CultivationExecutionActionEntity();
        batch.setId(id);
        batch.setUid("102550550");
        batch.setPlanRevision(2);
        batch.setExecutorId("inventory-history");
        batch.setMaterialName("__inventory_reconcile__");
        batch.setRemainingBefore(0L);
        batch.setPlanJson(json.writeValueAsString(owned.keySet()));
        batch.setStatus("COMPLETED");
        batch.setActionType("INVENTORY_RECONCILE_BATCH");
        batch.setRewardsJson(json.writeValueAsString(owned));
        batch.setResultIdempotencyKey(id + ":result");
        batch.setCreateTime(LocalDateTime.of(2026, 9, 7, 1, 0).plusSeconds(batchSequence++));
        return batch;
    }

    private static CultivationLedgerEntry entry(String name, long required, long owned, long remaining) {
        return new CultivationLedgerEntry(null, name, required, owned, remaining,
                RemainingEvidence.OCR, 1.0, false, List.of());
    }
}
