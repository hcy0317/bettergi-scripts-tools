package com.cloud_guest.cultivation.execution;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CultivationExperienceBookFamily {
    static final String FAMILY_NAME = "大英雄的经验";
    static final List<Tier> TIERS = List.of(
            new Tier("流浪者的经验", 1_000, 2),
            new Tier("冒险家的经验", 5_000, 3),
            new Tier(FAMILY_NAME, 20_000, 4));

    private CultivationExperienceBookFamily() {
    }

    static Optional<Tier> tier(String materialName) {
        return TIERS.stream().filter(value -> value.materialName().equals(materialName)).findFirst();
    }

    static long remainingTopTier(long requiredTopTier, Map<String, Long> ownedByName) {
        long topTierValue = TIERS.getLast().experiencePerItem();
        long requiredExperience = Math.multiplyExact(requiredTopTier, topTierValue);
        long ownedExperience = 0;
        for (Tier tier : TIERS) {
            long owned = Math.max(ownedByName.getOrDefault(tier.materialName(), 0L), 0L);
            ownedExperience = Math.addExact(
                    ownedExperience,
                    Math.multiplyExact(owned, tier.experiencePerItem()));
        }
        long missingExperience = Math.max(requiredExperience - ownedExperience, 0L);
        return (missingExperience + topTierValue - 1) / topTierValue;
    }

    record Tier(String materialName, long experiencePerItem, int qualityLevel) {
    }
}
