package com.cloud_guest.cultivation.execution;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationExperienceBookFamilyTest {
    @Test
    void convertsLowerTierBooksByExperienceInsteadOfRawCount() {
        assertThat(CultivationExperienceBookFamily.remainingTopTier(12, Map.of(
                "流浪者的经验", 4L,
                "冒险家的经验", 3L,
                "大英雄的经验", 10L)))
                .isEqualTo(2);
    }
}
