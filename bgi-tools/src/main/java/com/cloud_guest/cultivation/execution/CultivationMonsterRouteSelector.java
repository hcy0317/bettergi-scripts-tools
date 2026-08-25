package com.cloud_guest.cultivation.execution;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class CultivationMonsterRouteSelector {
    record Candidate(
            String option,
            int routeCount,
            int fightActions,
            int historicalFailures,
            boolean valid) {
        String author() {
            int marker = option.lastIndexOf('@');
            return marker >= 0 && marker + 1 < option.length()
                    ? option.substring(marker + 1)
                    : option;
        }

        double efficiency() {
            return routeCount <= 0 ? 0 : (double) fightActions / routeCount;
        }
    }

    private CultivationMonsterRouteSelector() {
    }

    static Map<String, String> select(Map<String, List<Candidate>> candidatesByFamily) {
        Map<String, Long> authorCoverage = candidatesByFamily.values().stream()
                .flatMap(Collection::stream)
                .filter(Candidate::valid)
                .collect(Collectors.groupingBy(
                        Candidate::author,
                        LinkedHashMap::new,
                        Collectors.counting()));

        Map<String, String> selected = new LinkedHashMap<>();
        candidatesByFamily.forEach((family, candidates) -> candidates.stream()
                .filter(Candidate::valid)
                .min(Comparator
                        .comparingInt(Candidate::historicalFailures)
                        .thenComparing(
                                candidate -> authorCoverage.getOrDefault(candidate.author(), 0L),
                                Comparator.reverseOrder())
                        .thenComparing(Candidate::efficiency, Comparator.reverseOrder())
                        .thenComparingInt(Candidate::routeCount)
                        .thenComparing(Candidate::option))
                .ifPresent(candidate -> selected.put(family, candidate.option())));
        return selected;
    }
}
