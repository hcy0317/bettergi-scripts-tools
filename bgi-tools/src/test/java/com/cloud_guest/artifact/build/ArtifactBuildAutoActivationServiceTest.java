package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.character.ArtifactCharacterRoster;
import com.cloud_guest.artifact.character.ArtifactCharacterRosterEntry;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactBuildAutoActivationServiceTest {

    @Test
    void enablesOnlyCharactersAboveTheThresholdWithFavoriteOverride() {
        InMemoryArtifactBuildRepository repository = new InMemoryArtifactBuildRepository();
        ArtifactBuildService buildService = new ArtifactBuildService(repository);
        buildService.importAll(List.of(
                build("furina", "Furina", false),
                build("noelle", "Noelle", false),
                build("clorinde", "Clorinde", true),
                build("diluc", "Diluc", true)));
        ArtifactBuildAutoActivationService service =
                new ArtifactBuildAutoActivationService(
                        buildService,
                        new InMemoryArtifactBuildAutoActivationResultRepository());

        var result = service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Furina", 90, false),
                        new ArtifactCharacterRosterEntry("Noelle", 70, true),
                        new ArtifactCharacterRosterEntry("Clorinde", 80, false))),
                new ArtifactBuildAutoActivationSettings(80, true));

        assertThat(result.characterCount()).isEqualTo(3);
        assertThat(result.favoriteCharacterCount()).isEqualTo(1);
        assertThat(result.levelEligibleCharacterCount()).isEqualTo(2);
        assertThat(result.eligibleCharacterCount()).isEqualTo(3);
        assertThat(result.enabledBuildCount()).isEqualTo(3);
        assertThat(result.disabledBuildCount()).isEqualTo(1);
        assertThat(buildService.list())
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled, ArtifactBuild::nativeSyncEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("clorinde", true, true),
                        org.assertj.core.groups.Tuple.tuple("diluc", false, false),
                        org.assertj.core.groups.Tuple.tuple("furina", true, true),
                        org.assertj.core.groups.Tuple.tuple("noelle", true, true));
    }

    @Test
    void favoriteOverrideCanBeDisabled() {
        InMemoryArtifactBuildRepository repository = new InMemoryArtifactBuildRepository();
        ArtifactBuildService buildService = new ArtifactBuildService(repository);
        buildService.importAll(List.of(build("noelle", "Noelle", true)));

        new ArtifactBuildAutoActivationService(
                buildService,
                new InMemoryArtifactBuildAutoActivationResultRepository()).apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Noelle", 79, true))),
                new ArtifactBuildAutoActivationSettings(80, false));

        assertThat(buildService.list().getFirst().analysisEnabled()).isFalse();
        assertThat(buildService.list().getFirst().nativeSyncEnabled()).isFalse();
    }

    private static ArtifactBuild build(String id, String characterKey, boolean enabled) {
        return new ArtifactBuild(
                id, id, characterKey, List.of(), Map.of("flower", Set.of("hp")),
                Map.of("critRate_", 1.0), enabled, enabled, "test");
    }
}
