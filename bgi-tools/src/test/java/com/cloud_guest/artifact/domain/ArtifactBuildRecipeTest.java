package com.cloud_guest.artifact.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactBuildRecipeTest {

    @Test
    void recipePiecesAreDerivedFromTheNumberOfSelectedSets() {
        ArtifactBuild fourPiece = build(List.of(new ArtifactSetRule("TenacityOfTheMillelith", 2)));
        ArtifactBuild twoPlusTwo = build(List.of(
                new ArtifactSetRule("MaidenBeloved", 4),
                new ArtifactSetRule("TenacityOfTheMillelith", 4)));

        assertThat(fourPiece.sets()).containsExactly(new ArtifactSetRule("TenacityOfTheMillelith", 4));
        assertThat(twoPlusTwo.sets()).containsExactly(
                new ArtifactSetRule("MaidenBeloved", 2),
                new ArtifactSetRule("TenacityOfTheMillelith", 2));
    }

    @Test
    void twoPieceRulesMatchSetsWithTheSameEffectButFourPieceRulesDoNot() {
        ArtifactBuild twoPlusTwo = build(List.of(
                new ArtifactSetRule("MaidenBeloved", 2),
                new ArtifactSetRule("TenacityOfTheMillelith", 2)));
        ArtifactBuild fourPiece = build(List.of(new ArtifactSetRule("TenacityOfTheMillelith", 4)));
        ArtifactItem vourukasha = artifact("VourukashasGlow");

        assertThat(twoPlusTwo.matchesSet(vourukasha)).isTrue();
        assertThat(fourPiece.matchesSet(vourukasha)).isFalse();
    }

    @Test
    void repeatedTwoPieceEffectSelectsTwoDistinctEquivalentSets() {
        ArtifactBuild build = build(List.of(
                new ArtifactSetRule("ShimenawasReminiscence", 2),
                new ArtifactSetRule("ShimenawasReminiscence", 2)));

        assertThat(build.sets()).extracting(ArtifactSetRule::setKey).doesNotHaveDuplicates();
        assertThat(build.sets()).allSatisfy(rule ->
                assertThat(ArtifactSetEffectCatalog.equivalentSetKeys(rule))
                        .contains("ShimenawasReminiscence"));
    }

    private static ArtifactBuild build(List<ArtifactSetRule> sets) {
        return new ArtifactBuild(
                "build", "build", "Furina", sets,
                Map.of("flower", java.util.Set.of("hp")), Map.of(),
                true, true, "source");
    }

    private static ArtifactItem artifact(String setKey) {
        return new ArtifactItem(0, setKey, "flower", 0, 5, "hp", List.of(), "", false);
    }
}
