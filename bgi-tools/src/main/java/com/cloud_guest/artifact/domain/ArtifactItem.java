package com.cloud_guest.artifact.domain;

import java.util.Comparator;
import java.util.List;

public record ArtifactItem(
        int scanIndex,
        String setKey,
        String slotKey,
        int level,
        int rarity,
        String mainStatKey,
        List<ArtifactSubstat> substats,
        String location,
        boolean locked) {

    public ArtifactItem {
        if (scanIndex < 0) throw new IllegalArgumentException("scan index must be nonnegative");
        if (setKey == null || setKey.isBlank()) throw new IllegalArgumentException("set key is required");
        if (slotKey == null || slotKey.isBlank()) throw new IllegalArgumentException("slot key is required");
        if (level < 0 || level > 20) throw new IllegalArgumentException("artifact level must be between 0 and 20");
        if (rarity < 1 || rarity > 5) throw new IllegalArgumentException("artifact rarity must be between 1 and 5");
        if (mainStatKey == null || mainStatKey.isBlank()) throw new IllegalArgumentException("main stat key is required");
        substats = substats == null ? List.of() : List.copyOf(substats);
        location = location == null ? "" : location;
    }

    public String contentFingerprint() {
        String canonicalSubstats = substats.stream()
                .sorted(Comparator.comparing(ArtifactSubstat::key)
                        .thenComparingDouble(ArtifactSubstat::value)
                        .thenComparing(ArtifactSubstat::dormant))
                .map(stat -> stat.key() + "=" + ArtifactHashes.decimal(stat.value())
                        + (stat.dormant() ? "@dormant" : ""))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        return ArtifactHashes.sha256(String.join("|",
                setKey, slotKey, Integer.toString(level), Integer.toString(rarity), mainStatKey,
                canonicalSubstats, location));
    }
}
