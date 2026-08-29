package com.cloud_guest.artifact.persistence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactStorageSchemaTest {

    @Test
    void mysqlDbKvValueCanHoldCompleteInventoryAnalysisJobs() throws Exception {
        try (var stream = getClass().getResourceAsStream("/sql/mysql.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("`value`       LONGTEXT");
        }
    }
}
