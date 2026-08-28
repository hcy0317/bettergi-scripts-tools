package com.cloud_guest.artifact.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSetRule;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.domain.ArtifactSubstat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactAnalysisEngineTest {

    private final ArtifactAnalysisEngine engine = new ArtifactAnalysisEngine();

    @Test
    void finishedPreferredMainUsesCanonicalRollsAndPublicScoreFlooring() {
        ArtifactItem artifact = artifact(20, "hp_", List.of(
                new ArtifactSubstat("critRate_", 11.7),
                new ArtifactSubstat("critDMG_", 23.3),
                new ArtifactSubstat("def", 23),
                new ArtifactSubstat("atk", 19)));

        ArtifactAnalysisResult result = engine.analyze(
                snapshot(artifact),
                List.of(build()),
                ArtifactAnalysisPolicy.defaults());

        ArtifactDecision decision = result.decisions().getFirst();
        assertThat(decision.preferredMain()).as(decision.toString()).isTrue();
        assertThat(decision.currentScore()).isEqualTo(89);
        assertThat(decision.potentialScore()).isEqualTo(decision.currentScore());
        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.KEEP);
        assertThat(decision.bestBuildId()).isEqualTo("furina-off-field");
    }

    @Test
    void decisionRetainsIndependentScoresForEveryEnabledBuild() {
        ArtifactItem artifact = artifact(20, "hp_", List.of(
                new ArtifactSubstat("critRate_", 11.7),
                new ArtifactSubstat("critDMG_", 23.3),
                new ArtifactSubstat("def", 23),
                new ArtifactSubstat("atk", 19)));
        ArtifactBuild alternate = new ArtifactBuild(
                "alternate", "通用", "Neuvillette",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("hp_")),
                Map.of("critRate_", 1.0, "critDMG_", 0.5, "hp_", 0.25),
                true, false, "custom");

        ArtifactAnalysisResult result = engine.analyze(
                        snapshot(artifact), List.of(build(), alternate),
                        ArtifactAnalysisPolicy.defaults());
        ArtifactDecision decision = result.decisions().getFirst();

        assertThat(result.buildIds()).containsExactly("furina-off-field", "alternate");
        assertThat(decision.buildCurrentScores()).hasSize(2).allSatisfy(score -> assertThat(score).isBetween(0, 100));
        assertThat(decision.buildPotentialScores()).hasSize(2).allSatisfy(score -> assertThat(score).isBetween(0, 100));
        assertThat(decision.buildPreferredMains()).hasSize(2);
        assertThat(decision.buildSetMatches()).hasSize(2);
    }

    @Test
    void historicalDecisionWithoutBuildScoresRemainsReadable() throws Exception {
        ArtifactDecision decision = new ObjectMapper().readValue("""
                {
                  "scanIndex": 0,
                  "expectedFingerprint": "fingerprint",
                  "expectedLocked": false,
                  "desiredLocked": true,
                  "bestBuildId": "furina-off-field",
                  "currentScore": 80,
                  "potentialScore": 90,
                  "preferredMain": true,
                  "setFit": "SET_MATCH",
                  "kind": "KEEP",
                  "reasons": []
                }
                """, ArtifactDecision.class);

        assertThat(decision.buildCurrentScores()).isEmpty();
        assertThat(decision.buildPotentialScores()).isEmpty();
    }

    @Test
    void unfinishedArtifactUsesExpectedFutureRollsForPotential() {
        ArtifactItem artifact = artifact(0, "critRate_", List.of(
                new ArtifactSubstat("critDMG_", 7.8),
                new ArtifactSubstat("hp_", 5.8),
                new ArtifactSubstat("enerRech_", 6.5)));

        ArtifactDecision decision = engine.analyze(
                        snapshot(artifact),
                        List.of(build()),
                        ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();

        assertThat(decision.currentScore()).isLessThan(75);
        assertThat(decision.potentialScore()).isGreaterThanOrEqualTo(75);
        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.KEEP);
    }

    @Test
    void dormantFourthLineIsExcludedFromCurrentAndActivatesBeforeFourRemainingRolls() {
        List<ArtifactSubstat> activeThree = List.of(
                new ArtifactSubstat("critRate_", 2.7),
                new ArtifactSubstat("critDMG_", 5.4),
                new ArtifactSubstat("def", 16));
        ArtifactItem threeLine = artifact(0, "hp_", activeThree);
        ArtifactItem dormantFourth = artifact(0, "hp_", List.of(
                activeThree.get(0), activeThree.get(1), activeThree.get(2),
                new ArtifactSubstat("enerRech_", 6.5, true)));
        ArtifactItem trueFourLine = artifact(0, "hp_", List.of(
                activeThree.get(0), activeThree.get(1), activeThree.get(2),
                new ArtifactSubstat("enerRech_", 6.5)));

        ArtifactDecision threeDecision = engine.analyze(
                        snapshot(threeLine), List.of(build()), ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();
        ArtifactDecision dormantDecision = engine.analyze(
                        snapshot(dormantFourth), List.of(build()), ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();
        ArtifactDecision fourLineDecision = engine.analyze(
                        snapshot(trueFourLine), List.of(build()), ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();

        assertThat(dormantDecision.currentScore()).isEqualTo(threeDecision.currentScore());
        assertThat(dormantDecision.currentScore()).isLessThan(fourLineDecision.currentScore());
        assertThat(dormantDecision.potentialScore()).isGreaterThan(dormantDecision.currentScore());
        assertThat(dormantDecision.potentialScore()).isLessThan(fourLineDecision.potentialScore());
        assertThat(dormantDecision.kind()).isNotEqualTo(ArtifactDecisionKind.UNSCORED);
        assertThat(dormantFourth.contentFingerprint()).isNotEqualTo(trueFourLine.contentFingerprint());
    }

    @Test
    void dormantSubstatOutsideLevelZeroIsRejected() {
        ArtifactItem invalid = artifact(4, "hp_", List.of(
                new ArtifactSubstat("critRate_", 2.7),
                new ArtifactSubstat("critDMG_", 5.4),
                new ArtifactSubstat("def", 16),
                new ArtifactSubstat("enerRech_", 6.5, true)));

        ArtifactDecision decision = engine.analyze(
                        snapshot(invalid), List.of(build()), ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();

        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.UNSCORED);
        assertThat(decision.reasons()).contains("INVALID_DORMANT_SUBSTAT");
    }

    @Test
    void historicalSubstatJsonDefaultsToActive() throws Exception {
        ArtifactSubstat substat = new ObjectMapper().readValue(
                "{\"key\":\"critRate_\",\"value\":3.1}", ArtifactSubstat.class);

        assertThat(substat.dormant()).isFalse();
    }

    @Test
    void wrongMainStatNeverBecomesKeepOnlyBecauseSubstatsScoreHighly() {
        ArtifactItem artifact = artifact(20, "def_", List.of(
                new ArtifactSubstat("critRate_", 11.7),
                new ArtifactSubstat("critDMG_", 23.3),
                new ArtifactSubstat("hp_", 5.8),
                new ArtifactSubstat("enerRech_", 6.5)));

        ArtifactDecision decision = engine.analyze(
                        snapshot(artifact),
                        List.of(build()),
                        ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();

        assertThat(decision.preferredMain()).isFalse();
        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.REJECT);
        assertThat(decision.reasons()).contains("MAIN_STAT_MISMATCH");
    }

    @Test
    void conditionalPotentialIgnoresThePopulationFourLineStartPrior() {
        ArtifactItem artifact = artifact(0, "hp_", List.of(
                new ArtifactSubstat("critRate_", 2.7),
                new ArtifactSubstat("critDMG_", 5.4),
                new ArtifactSubstat("def", 16)));

        ArtifactDecision defaultPrior = engine.analyze(
                        snapshot(artifact), List.of(build()),
                        new ArtifactAnalysisPolicy(0, 0, 0.2))
                .decisions().getFirst();
        ArtifactDecision differentPrior = engine.analyze(
                        snapshot(artifact), List.of(build()),
                        new ArtifactAnalysisPolicy(0, 0, 0.8))
                .decisions().getFirst();

        assertThat(defaultPrior.potentialScore()).isEqualTo(differentPrior.potentialScore());
        assertThat(defaultPrior.potentialScore()).isGreaterThan(defaultPrior.currentScore());
    }

    @Test
    void impossibleDisplayedRollIsLeftUnchangedAndUnscored() {
        ArtifactItem artifact = artifact(0, "hp_", List.of(
                new ArtifactSubstat("critRate_", 2.8),
                new ArtifactSubstat("critDMG_", 5.4),
                new ArtifactSubstat("def", 16)));

        ArtifactDecision decision = engine.analyze(
                        snapshot(artifact), List.of(build()), ArtifactAnalysisPolicy.defaults())
                .decisions().getFirst();

        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.UNSCORED);
        assertThat(decision.reasons()).contains("IMPOSSIBLE_SUBSTAT_VALUE");
        assertThat(decision.desiredLocked()).isEqualTo(decision.expectedLocked());
    }

    @Test
    void fourLinePlusZeroGoldenMatchesPinnedUpstreamExpectedPotential() {
        ArtifactItem artifact = new ArtifactItem(
                0, "GoldenTroupe", "circlet", 0, 5, "hp_",
                List.of(
                        new ArtifactSubstat("critRate_", 2.7),
                        new ArtifactSubstat("critDMG_", 5.4),
                        new ArtifactSubstat("def", 16),
                        new ArtifactSubstat("atk", 14)),
                "", false);
        ArtifactBuild goldenBuild = new ArtifactBuild(
                "golden", "golden", "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of("circlet", Set.of("hp_")),
                Map.of(
                        "critRate_", 1.0, "critDMG_", 1.0,
                        "enerRech_", 1.0, "eleMas", 1.0),
                true, false, "genshin-artifact-analyzer@766b1a6a");

        ArtifactDecision decision = engine.analyze(
                        snapshot(artifact), List.of(goldenBuild),
                        new ArtifactAnalysisPolicy(0, 0, 0.2))
                .decisions().getFirst();

        assertThat(decision.currentScore()).isEqualTo(55);
        assertThat(decision.potentialScore()).isEqualTo(67);
        assertThat(decision.kind()).isEqualTo(ArtifactDecisionKind.KEEP);
    }

    @Test
    void publicScoreReservesOneHundredForExactPerfection() {
        assertThat(ArtifactAnalysisEngine.publicScore(1.0)).isEqualTo(100);
        assertThat(ArtifactAnalysisEngine.publicScore(Math.nextDown(1.0))).isEqualTo(99);
    }

    @Test
    void contentFingerprintMatchesTheBetterGiHostContract() {
        ArtifactItem artifact = new ArtifactItem(
                0, "GoldenTroupe", "circlet", 20, 5, "critRate_",
                List.of(new ArtifactSubstat("critRate_", 7.8),
                        new ArtifactSubstat("critDMG_", 14.0)),
                "Furina", false);

        assertThat(artifact.contentFingerprint())
                .isEqualTo("2fff20ac9ec1dbec95328f0c529c842ff7996ebaf853b92a1812fa602f59a8a4");

        ArtifactItem dormant = new ArtifactItem(
                0, "GoldenTroupe", "circlet", 20, 5, "critRate_",
                List.of(new ArtifactSubstat("critRate_", 7.8),
                        new ArtifactSubstat("critDMG_", 14.0, true)),
                "Furina", false);
        assertThat(dormant.contentFingerprint())
                .isEqualTo("fe179e4e39f948a748465293bb9c6d1adcca18161f64ccd95d88f084dae6aaf2");
    }

    private static ArtifactSnapshot snapshot(ArtifactItem artifact) {
        return ArtifactSnapshot.create(
                "102550550", "scan-1", "OBTAINED_AT_DESC", "genshin-7.0", List.of(artifact));
    }

    private static ArtifactItem artifact(int level, String mainStat, List<ArtifactSubstat> substats) {
        return new ArtifactItem(
                0, "GoldenTroupe", "circlet", level, 5, mainStat,
                substats, "Furina", false);
    }

    private static ArtifactBuild build() {
        return new ArtifactBuild(
                "furina-off-field",
                "后台C",
                "Furina",
                List.of(new ArtifactSetRule("GoldenTroupe", 4)),
                Map.of(
                        "flower", Set.of("hp"),
                        "plume", Set.of("atk"),
                        "sands", Set.of("hp_", "enerRech_"),
                        "goblet", Set.of("hp_", "hydro_dmg_"),
                        "circlet", Set.of("critRate_", "critDMG_", "hp_")),
                Map.of("critRate_", 1.0, "critDMG_", 1.0, "hp_", 1.0, "enerRech_", 0.5),
                true,
                true,
                "genshin-artifact-analyzer@766b1a6a");
    }

}
