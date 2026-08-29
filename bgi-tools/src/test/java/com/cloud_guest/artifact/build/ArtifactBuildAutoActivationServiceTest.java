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
        assertThat(result.applied()).isTrue();
        assertThat(result.rosterDigest()).hasSize(64);
        assertThat(result.characters())
                .extracting(ArtifactCharacterRosterEntry::characterKey)
                .containsExactly("Clorinde", "Furina", "Noelle");
        assertThat(service.resolve("102550550", buildService.list()))
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

        ArtifactBuildAutoActivationService service = new ArtifactBuildAutoActivationService(
                buildService,
                new InMemoryArtifactBuildAutoActivationResultRepository());
        service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Noelle", 79, true))),
                new ArtifactBuildAutoActivationSettings(80, false));

        assertThat(service.resolve("102550550", buildService.list()).getFirst()
                .analysisEnabled()).isFalse();
        assertThat(service.resolve("102550550", buildService.list()).getFirst()
                .nativeSyncEnabled()).isFalse();
    }

    @Test
    void changedRosterMustBeObservedTwiceBeforeItCanChangeBuilds() {
        InMemoryArtifactBuildRepository repository = new InMemoryArtifactBuildRepository();
        ArtifactBuildService buildService = new ArtifactBuildService(repository);
        buildService.importAll(List.of(
                build("furina", "Furina", false),
                build("noelle", "Noelle", false)));
        ArtifactBuildAutoActivationService service = new ArtifactBuildAutoActivationService(
                buildService, new InMemoryArtifactBuildAutoActivationResultRepository());
        ArtifactBuildAutoActivationSettings settings =
                new ArtifactBuildAutoActivationSettings(80, true);
        service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Furina", 90, false))),
                settings);

        ArtifactBuildAutoActivationResult changed = service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Noelle", 90, false))),
                settings);

        assertThat(changed.applied()).isFalse();
        assertThat(changed.addedCharacterKeys()).containsExactly("Noelle");
        assertThat(changed.removedCharacterKeys()).containsExactly("Furina");
        assertThat(service.resolve("102550550", buildService.list()))
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina", true),
                        org.assertj.core.groups.Tuple.tuple("noelle", false));

        ArtifactBuildAutoActivationResult confirmed = service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Noelle", 90, false))),
                settings);

        assertThat(confirmed.applied()).isTrue();
        assertThat(confirmed.addedCharacterKeys()).isEmpty();
        assertThat(confirmed.removedCharacterKeys()).isEmpty();
        assertThat(service.resolve("102550550", buildService.list()))
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina", false),
                        org.assertj.core.groups.Tuple.tuple("noelle", true));
    }

    @Test
    void activationOverlayIsIsolatedByUid() {
        InMemoryArtifactBuildRepository repository = new InMemoryArtifactBuildRepository();
        ArtifactBuildService buildService = new ArtifactBuildService(repository);
        buildService.importAll(List.of(
                build("furina", "Furina", false),
                build("noelle", "Noelle", false)));
        ArtifactBuildAutoActivationService service = new ArtifactBuildAutoActivationService(
                buildService, new InMemoryArtifactBuildAutoActivationResultRepository());
        ArtifactBuildAutoActivationSettings settings =
                new ArtifactBuildAutoActivationSettings(80, false);

        service.apply(
                new ArtifactCharacterRoster("102550550", List.of(
                        new ArtifactCharacterRosterEntry("Furina", 90, false))),
                settings);
        service.apply(
                new ArtifactCharacterRoster("123456789", List.of(
                        new ArtifactCharacterRosterEntry("Noelle", 90, false))),
                settings);

        assertThat(service.resolve("102550550", buildService.list()))
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina", true),
                        org.assertj.core.groups.Tuple.tuple("noelle", false));
        assertThat(service.resolve("123456789", buildService.list()))
                .extracting(ArtifactBuild::id, ArtifactBuild::analysisEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("furina", false),
                        org.assertj.core.groups.Tuple.tuple("noelle", true));
    }

    @Test
    void legacyActivationWithoutRosterDigestFailsClosedUntilRescan() {
        InMemoryArtifactBuildRepository buildRepository =
                new InMemoryArtifactBuildRepository();
        ArtifactBuildService buildService = new ArtifactBuildService(buildRepository);
        buildService.importAll(List.of(build("furina", "Furina", true)));
        InMemoryArtifactBuildAutoActivationResultRepository resultRepository =
                new InMemoryArtifactBuildAutoActivationResultRepository();
        resultRepository.save("102550550", new ArtifactBuildAutoActivationResult(
                1, 0, 1, 1, 1, 0,
                new ArtifactBuildAutoActivationSettings(80, true),
                null, null, List.of(), List.of(), List.of(), List.of(), List.of()));
        ArtifactBuildAutoActivationService service =
                new ArtifactBuildAutoActivationService(buildService, resultRepository);

        assertThat(service.resolve("102550550", buildService.list()).getFirst()
                .analysisEnabled()).isFalse();

        service.clear("102550550");
        assertThat(service.resolve("102550550", buildService.list()).getFirst()
                .analysisEnabled()).isTrue();
    }

    private static ArtifactBuild build(String id, String characterKey, boolean enabled) {
        return new ArtifactBuild(
                id, id, characterKey, List.of(), Map.of("flower", Set.of("hp")),
                Map.of("critRate_", 1.0), enabled, enabled, "test");
    }
}
