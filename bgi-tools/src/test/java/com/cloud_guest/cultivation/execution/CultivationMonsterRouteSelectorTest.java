package com.cloud_guest.cultivation.execution;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CultivationMonsterRouteSelectorTest {
    @Test
    void selectsOneEfficientBundlePerFamily() {
        Map<String, List<CultivationMonsterRouteSelector.Candidate>> candidates = new LinkedHashMap<>();
        candidates.put("蕈兽", List.of(
                candidate("蕈兽@甲", 10, 10, 0),
                candidate("蕈兽@乙", 1, 8, 0)));

        assertThat(CultivationMonsterRouteSelector.select(candidates))
                .containsExactly(Map.entry("蕈兽", "蕈兽@乙"));
    }

    @Test
    void prefersTheSameAuthorAcrossFamiliesWhenStabilityIsEqual() {
        Map<String, List<CultivationMonsterRouteSelector.Candidate>> candidates = new LinkedHashMap<>();
        candidates.put("甲族", List.of(
                candidate("甲族@共享作者", 3, 6, 0),
                candidate("甲族@独立作者甲", 1, 3, 0)));
        candidates.put("乙族", List.of(
                candidate("乙族@共享作者", 2, 5, 0),
                candidate("乙族@独立作者乙", 1, 4, 0)));

        assertThat(CultivationMonsterRouteSelector.select(candidates))
                .containsExactly(
                        Map.entry("甲族", "甲族@共享作者"),
                        Map.entry("乙族", "乙族@共享作者"));
    }

    @Test
    void historicalFailuresOutrankAuthorReuse() {
        Map<String, List<CultivationMonsterRouteSelector.Candidate>> candidates = new LinkedHashMap<>();
        candidates.put("甲族", List.of(
                candidate("甲族@共享作者", 2, 8, 1),
                candidate("甲族@稳定作者", 3, 6, 0)));
        candidates.put("乙族", List.of(candidate("乙族@共享作者", 2, 8, 0)));

        assertThat(CultivationMonsterRouteSelector.select(candidates).get("甲族"))
                .isEqualTo("甲族@稳定作者");
    }

    private static CultivationMonsterRouteSelector.Candidate candidate(
            String option, int routeCount, int fightActions, int failures) {
        return new CultivationMonsterRouteSelector.Candidate(
                option, routeCount, fightActions, failures, true);
    }
}
