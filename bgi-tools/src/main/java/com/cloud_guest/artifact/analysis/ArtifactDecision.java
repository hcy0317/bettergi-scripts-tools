package com.cloud_guest.artifact.analysis;

import java.util.List;

public record ArtifactDecision(
        int scanIndex,
        String expectedFingerprint,
        boolean expectedLocked,
        boolean desiredLocked,
        String bestBuildId,
        double currentScore,
        double potentialScore,
        boolean preferredMain,
        String setFit,
        List<Integer> buildCurrentScores,
        List<Integer> buildPotentialScores,
        List<Boolean> buildPreferredMains,
        List<Boolean> buildSetMatches,
        ArtifactDecisionKind kind,
        List<String> reasons) {

    public ArtifactDecision {
        buildCurrentScores = buildCurrentScores == null ? List.of() : List.copyOf(buildCurrentScores);
        buildPotentialScores = buildPotentialScores == null ? List.of() : List.copyOf(buildPotentialScores);
        buildPreferredMains = buildPreferredMains == null ? List.of() : List.copyOf(buildPreferredMains);
        buildSetMatches = buildSetMatches == null ? List.of() : List.copyOf(buildSetMatches);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public ArtifactDecision(
            int scanIndex,
            String expectedFingerprint,
            boolean expectedLocked,
            boolean desiredLocked,
            String bestBuildId,
            double currentScore,
            double potentialScore,
            boolean preferredMain,
            String setFit,
            ArtifactDecisionKind kind,
            List<String> reasons) {
        this(scanIndex, expectedFingerprint, expectedLocked, desiredLocked, bestBuildId,
                currentScore, potentialScore, preferredMain, setFit,
                List.of(), List.of(), List.of(), List.of(), kind, reasons);
    }
}
