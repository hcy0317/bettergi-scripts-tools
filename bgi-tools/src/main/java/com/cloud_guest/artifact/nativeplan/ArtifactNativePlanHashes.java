package com.cloud_guest.artifact.nativeplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class ArtifactNativePlanHashes {
    private ArtifactNativePlanHashes() {
    }

    static String digest(
            int capacity,
            int sourceBuildCount,
            String translationMode,
            List<ArtifactNativeSetPlan> plans) {
        String canonicalPlans = plans.stream()
                .map(plan -> String.join("|",
                        plan.setKey(), plan.slotKey(),
                        plan.mainStats().stream().sorted().reduce((a, b) -> a + "," + b).orElse(""),
                        plan.substats().stream().sorted().reduce((a, b) -> a + "," + b).orElse("")))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String canonical = capacity + "|" + sourceBuildCount + "|" + translationMode + "\n" + canonicalPlans;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
