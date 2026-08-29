package com.cloud_guest.artifact.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ArtifactSnapshotTest {

    @Test
    void inventoryCountMayIncludeItemsThatAreNotAnalyzableArtifacts() {
        ArtifactSnapshot snapshot = snapshot(1125, List.of(artifact(0), artifact(1)));

        assertThat(snapshot.artifactCount()).isEqualTo(1125);
        assertThat(snapshot.artifacts()).hasSize(2);
    }

    @Test
    void inventoryCountCannotBeSmallerThanTheAnalyzableArtifactList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> snapshot(1, List.of(artifact(0), artifact(1))))
                .withMessage("artifact count cannot be smaller than items");
    }

    private static ArtifactSnapshot snapshot(int inventoryCount, List<ArtifactItem> artifacts) {
        return new ArtifactSnapshot(
                "102550550", "scan-1", inventoryCount,
                "OBTAINED_AT_DESC", "genshin-7.0", artifacts, "digest");
    }

    private static ArtifactItem artifact(int scanIndex) {
        return new ArtifactItem(
                scanIndex, "GladiatorsFinale", "flower", 0, 5,
                "hp", List.of(), "", false);
    }
}
