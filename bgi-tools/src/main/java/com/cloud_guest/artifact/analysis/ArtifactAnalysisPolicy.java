package com.cloud_guest.artifact.analysis;

public record ArtifactAnalysisPolicy(
        double unfinishedPotentialThreshold,
        double finishedScoreThreshold,
        double fourLineStartProbability) {

    public ArtifactAnalysisPolicy {
        if (!validScore(unfinishedPotentialThreshold) || !validScore(finishedScoreThreshold)) {
            throw new IllegalArgumentException("score thresholds must be in [0, 100]");
        }
        if (!Double.isFinite(fourLineStartProbability)
                || fourLineStartProbability < 0 || fourLineStartProbability > 1) {
            throw new IllegalArgumentException("four line start probability must be in [0, 1]");
        }
    }

    public static ArtifactAnalysisPolicy defaults() {
        return new ArtifactAnalysisPolicy(75, 80, 0.20);
    }

    private static boolean validScore(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 100;
    }
}
