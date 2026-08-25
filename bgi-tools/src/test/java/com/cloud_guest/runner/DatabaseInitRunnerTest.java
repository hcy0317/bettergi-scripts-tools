package com.cloud_guest.runner;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseInitRunnerTest {

    @Test
    void refusesStartupWhenTheCultivationExecutionTableIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cultivation_execution_action WHERE 1 = 0", Integer.class))
                .thenThrow(new IllegalStateException("missing table"));
        DatabaseInitRunner runner = runner(jdbcTemplate);

        assertThatThrownBy(runner::verifyCultivationExecutionSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝");
    }

    @Test
    void acceptsStartupWhenTheCultivationExecutionTableIsQueryable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cultivation_execution_action WHERE 1 = 0", Integer.class))
                .thenReturn(0);

        assertThatCode(runner(jdbcTemplate)::verifyCultivationExecutionSchema).doesNotThrowAnyException();
    }

    private static DatabaseInitRunner runner(JdbcTemplate jdbcTemplate) {
        return new DatabaseInitRunner(
                mock(DataSource.class), mock(ResourceLoader.class), mock(Scheduler.class), jdbcTemplate);
    }
}
