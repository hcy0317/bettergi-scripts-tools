package com.cloud_guest.cultivation.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CultivationDatabaseSchemaTest {

    @Test
    void mysqlAndPostgresKeepUnboundedTerminationDiagnostics() throws Exception {
        String mysql = new ClassPathResource("sql/mysql.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String postgres = new ClassPathResource("sql/pgsql.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mysql).contains("`termination_reason` text");
        assertThat(postgres)
                .contains("termination_reason TEXT")
                .contains("ADD COLUMN IF NOT EXISTS is_delete BOOLEAN DEFAULT false;");
    }

    @Test
    void sqliteInitializationIsIdempotentAndRevisionIsUniquePerUid() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ClassPathResource script = new ClassPathResource("sql/sqlite.sql");
            ScriptUtils.executeSqlScript(connection, script);
            ScriptUtils.executeSqlScript(connection, script);

            assertThat(tableExists(connection, "cultivation_import_preview")).isTrue();
            assertThat(tableExists(connection, "cultivation_plan_revision")).isTrue();
            assertThat(tableExists(connection, "cultivation_module_config")).isTrue();
            assertThat(tableExists(connection, "cultivation_execution_action")).isTrue();
            assertThat(columnExists(connection, "uid_info_config", "is_default")).isTrue();

            String insert = """
                    INSERT INTO cultivation_plan_revision
                    (id, uid, revision, state, catalog_version, preview_id,
                     source_image_sha256, engine_version, model_source, requirements_json)
                    VALUES (%d, '123456789', 1, 'IMPORTED', 'name-only-v1', 42,
                            'hash', 'PP-OCRv6', 'bettergi-installed-assets', '[]')
                    """;
            connection.createStatement().executeUpdate(insert.formatted(1));
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(insert.formatted(2)))
                    .isInstanceOf(SQLException.class);

            String moduleInsert = """
                    INSERT INTO cultivation_module_config
                    (id, uid, module_id, adapter_version, enabled, settings_json)
                    VALUES (%d, '123456789', 'auto-plan-resin', '1.0', 1, '{}')
                    """;
            connection.createStatement().executeUpdate(moduleInsert.formatted(10));
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(moduleInsert.formatted(11)))
                    .isInstanceOf(SQLException.class);

            String actionInsert = """
                    INSERT INTO cultivation_execution_action
                    (id, uid, plan_revision, executor_id, lease_key, status, action_type,
                     material_name, remaining_before, plan_json)
                    VALUES ('%s', '123456789', 1, 'executor', '123456789:1', 'LEASED',
                            'DOMAIN', '「公平」的哲学', 5, '{}')
                    """;
            connection.createStatement().executeUpdate(actionInsert.formatted("action-1"));
            assertThatThrownBy(() -> connection.createStatement()
                    .executeUpdate(actionInsert.formatted("action-2")))
                    .isInstanceOf(SQLException.class);

            connection.createStatement().executeUpdate(
                    "UPDATE cultivation_execution_action SET lease_key = NULL WHERE id = 'action-1'");
            connection.createStatement().executeUpdate("""
                    INSERT INTO cultivation_execution_action
                    (id, uid, plan_revision, executor_id, lease_key, lease_expires_at, status, action_type,
                     material_name, remaining_before, plan_json)
                    VALUES ('inventory-batch', '123456789', 1, 'inventory-executor', '123456789:1',
                            '2026-08-26 04:00:00', 'LEASED', 'INVENTORY_RECONCILE_BATCH',
                            '__inventory_reconcile__', 234, '["沙脂蛹","织金红绸"]')
                    """);
            connection.createStatement().executeUpdate("""
                    UPDATE cultivation_execution_action
                    SET lease_key = NULL, status = 'COMPLETED',
                        rewards_json = '{"沙脂蛹":48,"织金红绸":73}',
                        result_idempotency_key = 'inventory-batch:result'
                    WHERE id = 'inventory-batch' AND status = 'LEASED'
                    """);
            try (ResultSet result = connection.createStatement().executeQuery("""
                    SELECT status, rewards_json FROM cultivation_execution_action
                    WHERE id = 'inventory-batch'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("COMPLETED");
                assertThat(result.getString("rewards_json")).contains("沙脂蛹");
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet result = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return result.next() && result.getInt(1) == 1;
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet result = connection.createStatement().executeQuery(
                "PRAGMA table_info('" + tableName + "')")) {
            while (result.next()) {
                if (columnName.equals(result.getString("name"))) return true;
            }
            return false;
        }
    }
}
