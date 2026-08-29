package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactPresetInitializerTest {

    @Test
    void initializerUsesTheSameDatabaseInitConditionAsItsDependency() {
        ConditionalOnProperty condition = AnnotatedElementUtils.findMergedAnnotation(
                ArtifactPresetInitializer.class, ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("spring.datasource.init");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void localizesExistingUpstreamNamesWithoutOverwritingUserActivationChoices() {
        ArtifactPresetCatalog catalog = new ArtifactPresetCatalog(new ObjectMapper());
        ArtifactBuild localized = catalog.builds().stream()
                .filter(build -> build.id().equals("preset-112-clorinde-aggravate-dps"))
                .findFirst()
                .orElseThrow();
        InMemoryArtifactBuildRepository repository = new InMemoryArtifactBuildRepository();
        repository.save(new ArtifactBuild(
                localized.id(), "AGGRAVATE_DPS", localized.characterKey(), localized.sets(),
                localized.alternativeSetRecipes(), localized.mainStatsBySlot(), localized.substatWeights(),
                false, false, localized.sourceVersion()));

        new ArtifactPresetInitializer(new ArtifactBuildService(repository), catalog).initialize();

        ArtifactBuild migrated = repository.findById(localized.id()).orElseThrow();
        assertThat(repository.findAll()).hasSize(1);
        assertThat(migrated.name()).isEqualTo("激化输出");
        assertThat(migrated.analysisEnabled()).isFalse();
        assertThat(migrated.nativeSyncEnabled()).isFalse();
    }
}
