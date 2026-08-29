package com.cloud_guest.artifact.analysis;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ArtifactRollValues {
    private static final int[] TIER_POINTS = {7, 8, 9, 10};
    private static final Map<String, double[]> TIERS = Map.ofEntries(
            Map.entry("hp", new double[]{209.13, 239, 268.88, 298.75}),
            Map.entry("atk", new double[]{13.62, 15.56, 17.51, 19.45}),
            Map.entry("def", new double[]{16.2, 18.52, 20.83, 23.15}),
            Map.entry("hp_", new double[]{4.08, 4.66, 5.25, 5.83}),
            Map.entry("atk_", new double[]{4.08, 4.66, 5.25, 5.83}),
            Map.entry("def_", new double[]{5.10, 5.83, 6.56, 7.29}),
            Map.entry("eleMas", new double[]{16.32, 18.65, 20.98, 23.31}),
            Map.entry("enerRech_", new double[]{4.53, 5.18, 5.83, 6.48}),
            Map.entry("critRate_", new double[]{2.72, 3.11, 3.50, 3.89}),
            Map.entry("critDMG_", new double[]{5.44, 6.22, 6.99, 7.77}));
    private static final Map<String, Integer> TYPE_WEIGHTS = Map.ofEntries(
            Map.entry("hp", 150), Map.entry("atk", 150), Map.entry("def", 150),
            Map.entry("hp_", 100), Map.entry("atk_", 100), Map.entry("def_", 100),
            Map.entry("eleMas", 100), Map.entry("enerRech_", 100),
            Map.entry("critRate_", 75), Map.entry("critDMG_", 75));
    private static final Set<String> PERCENTAGE_TYPES = Set.of(
            "hp_", "atk_", "def_", "enerRech_", "critRate_", "critDMG_");
    private static final Map<String, Map<Integer, CanonicalRoll>> LOOKUPS = buildLookups();

    private ArtifactRollValues() {
    }

    static CanonicalRoll canonicalRoll(String statKey, double displayedValue) {
        Map<Integer, CanonicalRoll> lookup = LOOKUPS.get(statKey);
        if (lookup == null || !Double.isFinite(displayedValue)) return null;
        double scaled = PERCENTAGE_TYPES.contains(statKey) ? displayedValue * 10.0 : displayedValue;
        int key = (int) Math.round(scaled);
        if (Math.abs(scaled - key) > 1e-4) return null;
        return lookup.get(key);
    }

    static Map<String, Integer> typeWeights() {
        return TYPE_WEIGHTS;
    }

    private static Map<String, Map<Integer, CanonicalRoll>> buildLookups() {
        Map<String, Map<Integer, CanonicalRoll>> lookups = new LinkedHashMap<>();
        TIERS.forEach((type, tiers) -> {
            Map<Integer, MutableRoll> mutable = new LinkedHashMap<>();
            visit(type, tiers, 0, 0, 0, mutable);
            Map<Integer, CanonicalRoll> immutable = new LinkedHashMap<>();
            mutable.forEach((key, value) -> immutable.put(
                    key, new CanonicalRoll(value.rollValuePoints, Set.copyOf(value.possibleRollCounts))));
            lookups.put(type, Map.copyOf(immutable));
        });
        return Map.copyOf(lookups);
    }

    private static void visit(
            String type,
            double[] tiers,
            float aggregate,
            int rollCount,
            int rollValuePoints,
            Map<Integer, MutableRoll> lookup) {
        if (rollCount >= 6) return;
        for (int index = 0; index < tiers.length; index++) {
            float nextAggregate = (float) (aggregate + (float) tiers[index]);
            int nextCount = rollCount + 1;
            int nextPoints = rollValuePoints + TIER_POINTS[index];
            int displayKey = PERCENTAGE_TYPES.contains(type)
                    ? Math.round(nextAggregate * 10)
                    : Math.round(nextAggregate);
            MutableRoll current = lookup.get(displayKey);
            if (current != null && current.rollValuePoints != nextPoints) {
                throw new IllegalStateException("ambiguous artifact roll lookup for " + type + "=" + displayKey);
            }
            if (current == null) {
                current = new MutableRoll(nextPoints);
                lookup.put(displayKey, current);
            }
            current.possibleRollCounts.add(nextCount);
            visit(type, tiers, nextAggregate, nextCount, nextPoints, lookup);
        }
    }

    record CanonicalRoll(int rollValuePoints, Set<Integer> possibleRollCounts) {
    }

    private static final class MutableRoll {
        private final int rollValuePoints;
        private final Set<Integer> possibleRollCounts = new LinkedHashSet<>();

        private MutableRoll(int rollValuePoints) {
            this.rollValuePoints = rollValuePoints;
        }
    }
}
