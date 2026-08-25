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
