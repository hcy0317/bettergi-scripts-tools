package com.cloud_guest.artifact.analysis;

import com.cloud_guest.artifact.domain.ArtifactBuild;
import com.cloud_guest.artifact.domain.ArtifactItem;
import com.cloud_guest.artifact.domain.ArtifactSnapshot;
import com.cloud_guest.artifact.domain.ArtifactSubstat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArtifactAnalysisEngine {
    public static final String POLICY_VERSION = "gaa-766b1a6a-public-score-v2-dormant-substat";
    private static final double EPSILON = 1e-12;

    public ArtifactAnalysisResult analyze(
            ArtifactSnapshot snapshot,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        List<ArtifactBuild> enabledBuilds = builds.stream()
                .filter(ArtifactBuild::analysisEnabled)
                .toList();
        List<ArtifactDecision> decisions = snapshot.artifacts().stream()
                .map(artifact -> decide(artifact, enabledBuilds, policy))
                .toList();
        return new ArtifactAnalysisResult(
                snapshot.snapshotDigest(), POLICY_VERSION,
                enabledBuilds.stream().map(ArtifactBuild::id).toList(),
                decisions, summarize(decisions));
    }

    private ArtifactDecision decide(
            ArtifactItem artifact,
            List<ArtifactBuild> builds,
            ArtifactAnalysisPolicy policy) {
        List<String> mechanicsIssues = validateMechanics(artifact);
        if (artifact.rarity() != 5 || builds.isEmpty() || !mechanicsIssues.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            if (artifact.rarity() != 5) reasons.add("UNSUPPORTED_RARITY");
            if (builds.isEmpty()) reasons.add("NO_ENABLED_BUILD");
            reasons.addAll(mechanicsIssues);
            return new ArtifactDecision(
                    artifact.scanIndex(), artifact.contentFingerprint(), artifact.locked(), artifact.locked(),
                    "", 0, 0, false, "UNSCORED", ArtifactDecisionKind.UNSCORED, reasons);
        }

        List<BuildEvaluation> evaluations = builds.stream()
                .map(build -> evaluate(artifact, build))
                .toList();
        BuildEvaluation best = null;
        for (BuildEvaluation candidate : evaluations) {
            if (best == null || isBetter(artifact, candidate, best)) best = candidate;
        }
        if (best == null) throw new IllegalStateException("no enabled artifact build could be evaluated");

        List<Integer> buildCurrentScores = evaluations.stream()
                .map(evaluation -> publicScore(evaluation.currentMatch()))
                .toList();
        List<Integer> buildPotentialScores = evaluations.stream()
                .map(evaluation -> publicScore(evaluation.potentialMatch()))
                .toList();
        List<Boolean> buildPreferredMains = evaluations.stream()
                .map(BuildEvaluation::preferredMain)
                .toList();
        List<Boolean> buildSetMatches = evaluations.stream()
                .map(evaluation -> "SET_MATCH".equals(evaluation.setFit()))
                .toList();

        int currentScore = publicScore(best.currentMatch());
        int potentialScore = publicScore(best.potentialMatch());
        boolean passesThreshold = artifact.level() >= 20
                ? currentScore >= policy.finishedScoreThreshold()
                : potentialScore >= policy.unfinishedPotentialThreshold();
        ArtifactDecisionKind kind = best.preferredMain() && passesThreshold
                ? ArtifactDecisionKind.KEEP : ArtifactDecisionKind.REJECT;
        List<String> reasons = new ArrayList<>();
        if (!best.preferredMain()) reasons.add("MAIN_STAT_MISMATCH");
        if (best.preferredMain() && !passesThreshold) reasons.add("BELOW_SCORE_THRESHOLD");
        reasons.add(best.setFit());
        return new ArtifactDecision(
                artifact.scanIndex(), artifact.contentFingerprint(), artifact.locked(),
                kind == ArtifactDecisionKind.KEEP, best.build().id(),
                currentScore, potentialScore, best.preferredMain(), best.setFit(),
                buildCurrentScores, buildPotentialScores, buildPreferredMains, buildSetMatches,
                kind, reasons);
    }

    private static boolean isBetter(
            ArtifactItem artifact,
            BuildEvaluation candidate,
            BuildEvaluation current) {
        double candidatePrimary = artifact.level() >= 20
                ? candidate.currentMatch() : candidate.potentialMatch();
        double currentPrimary = artifact.level() >= 20
                ? current.currentMatch() : current.potentialMatch();
        if (candidatePrimary > currentPrimary + EPSILON) return true;
        return artifact.level() < 20
                && Math.abs(candidatePrimary - currentPrimary) <= EPSILON
                && candidate.currentMatch() > current.currentMatch() + EPSILON;
    }

    private BuildEvaluation evaluate(ArtifactItem artifact, ArtifactBuild build) {
        Map<String, Double> legalImportances = new LinkedHashMap<>();
        build.substatWeights().forEach((key, weight) -> {
            if (weight > 0 && !key.equals(artifact.mainStatKey())) legalImportances.put(key, weight);
        });
        List<Double> topFour = legalImportances.values().stream()
                .sorted(Comparator.reverseOrder()).limit(4).toList();
        double maximumImportance = topFour.isEmpty() ? 0 : topFour.getFirst();
        double denominatorImportance = topFour.stream().mapToDouble(Double::doubleValue).sum()
                + 5 * maximumImportance;
        double currentWeightedPoints = 0;
        double existingImportance = 0;
        double dormantWeightedPoints = 0;
        for (ArtifactSubstat substat : artifact.substats()) {
            double importance = legalImportances.getOrDefault(substat.key(), 0.0);
            ArtifactRollValues.CanonicalRoll roll = ArtifactRollValues.canonicalRoll(
                    substat.key(), substat.value());
            if (roll == null) throw new IllegalStateException("validated artifact lost its canonical roll");
            double weightedPoints = importance * roll.rollValuePoints();
            if (substat.dormant()) dormantWeightedPoints += weightedPoints;
            else currentWeightedPoints += weightedPoints;
            existingImportance += importance;
        }
        boolean preferredMain = build.acceptsMainStat(artifact);
        double currentMatch = match(currentWeightedPoints, preferredMain, denominatorImportance);
        double expectedFinalWeightedPoints = currentWeightedPoints;
        if (artifact.substats().stream().anyMatch(ArtifactSubstat::dormant)) {
            expectedFinalWeightedPoints += dormantWeightedPoints
                    + 4 * (17.0 / 8.0) * existingImportance;
        } else if (artifact.substats().size() == 3) {
            expectedFinalWeightedPoints += 8.5 * existingImportance
                    + 17 * expectedFourthLineImportance(artifact, legalImportances);
        } else {
            int milestone = Math.floorDiv(artifact.level(), 4) * 4;
            int remainingUpgradeEvents = Math.max(0, 5 - milestone / 4);
            expectedFinalWeightedPoints += remainingUpgradeEvents * (17.0 / 8.0) * existingImportance;
        }
        double potentialMatch = match(
                expectedFinalWeightedPoints, preferredMain, denominatorImportance);
        String setFit = build.matchesSet(artifact) ? "SET_MATCH" : "OFF_PIECE_CANDIDATE";
        return new BuildEvaluation(build, preferredMain, currentMatch, potentialMatch, setFit);
    }

    private static double expectedFourthLineImportance(
            ArtifactItem artifact,
            Map<String, Double> legalImportances) {
        Set<String> excluded = new HashSet<>();
        excluded.add(artifact.mainStatKey());
        artifact.substats().forEach(stat -> excluded.add(stat.key()));
        double totalTypeWeight = 0;
        double weightedImportance = 0;
        for (Map.Entry<String, Integer> entry : ArtifactRollValues.typeWeights().entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            totalTypeWeight += entry.getValue();
            weightedImportance += entry.getValue() * legalImportances.getOrDefault(entry.getKey(), 0.0);
        }
        return totalTypeWeight == 0 ? 0 : weightedImportance / totalTypeWeight;
    }

    private static double match(
            double weightedRollPoints,
            boolean preferredMain,
            double denominatorImportance) {
        double mainContribution = preferredMain ? 8.0 / 17.0 : 0;
        if (denominatorImportance == 0) return mainContribution;
        double value = mainContribution
                + 9 * weightedRollPoints / (170 * denominatorImportance);
        return Math.min(1, Math.max(0, value));
    }

    static int publicScore(double value) {
        if (value == 1.0) return 100;
        double scaled = value * 100;
        double nearestInteger = Math.rint(scaled);
        double magnitude = Math.max(1, Math.abs(scaled));
        double tolerance = Math.max(4 * Math.ulp(magnitude), Math.scalb(magnitude, -23));
        double stableScaled = Math.abs(scaled - nearestInteger) <= tolerance
                ? nearestInteger : scaled;
        return Math.min(99, Math.max(0, (int) Math.floor(stableScaled)));
    }

    private static List<String> validateMechanics(ArtifactItem artifact) {
        if (artifact.rarity() != 5) return List.of();
        int milestone = Math.floorDiv(artifact.level(), 4) * 4;
        long dormantCount = artifact.substats().stream().filter(ArtifactSubstat::dormant).count();
        if (dormantCount > 1 || (dormantCount == 1
                && (artifact.level() != 0 || artifact.substats().size() != 4))) {
            return List.of("INVALID_DORMANT_SUBSTAT");
        }
        if ((artifact.substats().size() != 3 && artifact.substats().size() != 4)
                || (artifact.substats().size() == 3 && milestone >= 4)) {
            return List.of("INVALID_VISIBLE_LINE_COUNT");
        }
        Set<String> types = new HashSet<>();
        List<Set<Integer>> possibleCounts = new ArrayList<>();
        for (ArtifactSubstat substat : artifact.substats()) {
            if (!types.add(substat.key())) return List.of("DUPLICATE_SUBSTAT");
            if (substat.key().equals(artifact.mainStatKey())) return List.of("SUBSTAT_EQUALS_MAIN_STAT");
            ArtifactRollValues.CanonicalRoll roll = ArtifactRollValues.canonicalRoll(
                    substat.key(), substat.value());
            if (roll == null) return List.of("IMPOSSIBLE_SUBSTAT_VALUE");
            possibleCounts.add(roll.possibleRollCounts());
        }
        Set<Integer> totals = Set.of(0);
        for (Set<Integer> counts : possibleCounts) {
            Set<Integer> next = new HashSet<>();
            for (int total : totals) for (int count : counts) next.add(total + count);
            totals = next;
        }
        int upgrades = milestone / 4;
        boolean legal = artifact.substats().size() == 3
                ? upgrades == 0 && totals.contains(3)
                : upgrades == 0
                ? totals.contains(4)
                : totals.contains(3 + upgrades) || totals.contains(4 + upgrades);
        return legal ? List.of() : List.of("IMPOSSIBLE_TOTAL_ROLL_COUNT");
    }

    private static ArtifactDecisionSummary summarize(List<ArtifactDecision> decisions) {
        int keep = 0;
        int reject = 0;
        int unscored = 0;
        int lock = 0;
        int unlock = 0;
        int unchanged = 0;
        for (ArtifactDecision decision : decisions) {
            switch (decision.kind()) {
                case KEEP -> keep++;
                case REJECT -> reject++;
                case UNSCORED -> unscored++;
            }
            if (decision.expectedLocked() == decision.desiredLocked()) unchanged++;
            else if (decision.desiredLocked()) lock++;
            else unlock++;
        }
        return new ArtifactDecisionSummary(keep, reject, unscored, lock, unlock, unchanged);
    }

    private record BuildEvaluation(
            ArtifactBuild build,
            boolean preferredMain,
            double currentMatch,
            double potentialMatch,
            String setFit) {
    }
}
