package com.cloud_guest.artifact.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactPresetCatalogTest {

    @Test
    void loadsTheCompletePinnedUpstreamCatalog() {
        var builds = new ArtifactPresetCatalog(new ObjectMapper()).builds();

        assertThat(builds).hasSize(158);
        assertThat(builds)
                .extracting(build -> build.name())
                .allMatch(name -> !name.matches(".*[A-Za-z].*"));
        assertThat(builds).allMatch(build -> build.analysisEnabled() && build.nativeSyncEnabled());
        assertThat(builds).allMatch(build -> build.sourceVersion()
                .equals("genshin-artifact-analyzer@766b1a6af0757afce1938da2b25f306ef8079838"));
        assertThat(builds).anyMatch(build -> !build.alternativeSetRecipes().isEmpty());
    }
}
