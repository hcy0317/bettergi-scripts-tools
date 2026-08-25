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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DatabaseInitRunnerTest {
    @TempDir
    Path temporaryDirectory;

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
