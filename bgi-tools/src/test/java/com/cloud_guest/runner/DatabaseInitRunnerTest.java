package com.cloud_guest.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.Scheduler;
import org.sqlite.SQLiteDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DatabaseInitRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyPlanMigrationPreservesRawDataAndProcessesEveryBatchWithoutControllers() throws Exception {
        DataSource source = dataSource("legacy-plans.db");
        String payload = "{\"bossName\":\"test-boss\",\"futureOption\":{\"keep\":true}}";
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/sqlite.sql"));
            // 启动流程先补充兼容列，再迁移旧 JSON；这里构造同一阶段的数据库。
            connection.createStatement().execute("ALTER TABLE auto_plan_config ADD COLUMN cultivate INTEGER DEFAULT 0");
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO auto_plan_config(id,uid,col_order,days,day_name,run_type,enable,cultivate,record,auto_boss)
                    VALUES (?,'123456789',7,'1,3','legacy','Boss',1,1,1,?)
                    """)) {
                for (int id = 1; id <= 1005; id++) {
                    insert.setInt(1, id);
                    insert.setString(2, payload);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        }
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO auto_plan_config(id,uid,run_type,json,auto_boss) VALUES (2000,'other','Boss','{\"current\":true}',?)", payload);
        jdbc.update("INSERT INTO auto_plan_config(id,uid,run_type,auto_boss) VALUES (2001,'other','future-type',?)", payload);

        DatabaseInitRunner runner = runner(source);
        assertThat(runner.migrateLegacyAutoPlanJson()).isEqualTo(1005);
        assertThat(runner.migrateLegacyAutoPlanJson()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM auto_plan_config WHERE id<=1005 AND json=auto_boss
                AND uid='123456789' AND col_order=7 AND days='1,3' AND day_name='legacy'
                AND enable=1 AND cultivate=1 AND record=1
                """, Integer.class)).isEqualTo(1005);
        assertThat(jdbc.queryForObject("SELECT json FROM auto_plan_config WHERE id=1", String.class)).isEqualTo(payload);
        assertThat(jdbc.queryForObject("SELECT json FROM auto_plan_config WHERE id=2000", String.class)).isEqualTo("{\"current\":true}");
        assertThat(jdbc.queryForObject("SELECT json FROM auto_plan_config WHERE id=2001", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auto_plan_config", Integer.class)).isEqualTo(1007);
    }

    @Test
    void refusesStartupWhenRequiredColumnsOrIndexesAreMissing() throws Exception {
        DataSource dataSource = dataSource("partial.db");
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                    CREATE TABLE cultivation_execution_action (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL
                    )
                    """);
        }

        assertThatThrownBy(runner(dataSource)::verifyCultivationExecutionSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("约束不完整")
                .rootCause()
                .hasMessageContaining("缺少必要列");
    }

    @Test
    void acceptsTheCompleteSqliteCultivationExecutionContract() throws Exception {
        DataSource dataSource = dataSource("complete.db");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/sqlite.sql"));
        }

        assertThatCode(runner(dataSource)::verifyCultivationExecutionSchema).doesNotThrowAnyException();
    }

    @Test
    void refusesCompositeUniqueIndexesThatDoNotEnforceSingleColumnLeaseKeys() throws Exception {
        DataSource dataSource = dataSource("composite-unique.db");
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                    CREATE TABLE cultivation_execution_action (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        plan_revision INTEGER NOT NULL,
                        executor_id TEXT NOT NULL,
                        lease_key TEXT,
                        lease_expires_at TEXT,
                        status TEXT NOT NULL,
                        action_type TEXT NOT NULL,
                        material_name TEXT NOT NULL,
                        remaining_before INTEGER NOT NULL,
                        plan_json TEXT NOT NULL,
                        observed_owned INTEGER,
                        rewards_json TEXT,
                        termination_reason TEXT,
                        result_idempotency_key TEXT,
                        create_by TEXT,
                        create_time TEXT,
                        update_by TEXT,
                        update_time TEXT,
                        remark TEXT,
                        UNIQUE (lease_key, status),
                        UNIQUE (result_idempotency_key, status)
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE INDEX idx_cultivation_action_uid_revision
                    ON cultivation_execution_action (uid, plan_revision, status)
                    """);
        }

        assertThatThrownBy(runner(dataSource)::verifyCultivationExecutionSchema)
                .isInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("单列唯一约束");
    }

    private DataSource dataSource(String fileName) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve(fileName));
        return dataSource;
    }

    private static DatabaseInitRunner runner(DataSource dataSource) {
        return new DatabaseInitRunner(
                dataSource, mock(ResourceLoader.class), mock(Scheduler.class), new JdbcTemplate(dataSource));
    }
}
