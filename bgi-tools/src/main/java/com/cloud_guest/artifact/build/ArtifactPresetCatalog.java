package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ArtifactPresetCatalog {
    private final List<ArtifactBuild> builds;

    public ArtifactPresetCatalog(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("artifact/recommended-builds.json").getInputStream()) {
            builds = List.copyOf(objectMapper.readValue(input, new TypeReference<List<ArtifactBuild>>() { }));
        } catch (IOException exception) {
            throw new IllegalStateException("unable to load pinned artifact build catalog", exception);
        }
        if (builds.size() != 158) {
            throw new IllegalStateException("pinned artifact build catalog must contain 158 builds");
        }
    }

    public List<ArtifactBuild> builds() {
        return builds;
    }
}
