package com.cloud_guest.artifact.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArtifactSetEffectCatalog {
    private static final List<List<String>> TWO_PIECE_GROUPS = List.of(
            List.of("BlizzardStrayer", "FinaleOfTheDeepGalleries"),
            List.of("BloodstainedChivalry", "PaleFlame"),
            List.of(
                    "BraveHeart", "EchoesOfAnOffering", "GladiatorsFinale", "ResolutionOfSojourner",
                    "ShimenawasReminiscence", "VermillionHereafter", "NighttimeWhispersInTheEchoingWoods",
                    "FragmentOfHarmonicWhimsy", "UnfinishedReverie", "ADayCarvedFromRisingWinds",
                    "DisenchantmentInDeepShadow", "HeartOfTheFurnace", "ScarletProof"),
            List.of("DefendersWill", "HuskOfOpulentDreams"),
            List.of("DesertPavilionChronicle", "ViridescentVenerer"),
            List.of("EmblemOfSeveredFate", "Scholar", "TheExile", "SilkenMoonsSerenade", "CelestialGift"),
            List.of(
                    "FlowerOfParadiseLost", "GildedDreams", "Instructor", "WanderersTroupe",
                    "NightOfTheSkysUnveiling", "AubadeOfMorningstarAndMoon"),
            List.of("Gambler", "GoldenTroupe"),
            List.of("HeartOfDepth", "NymphsDream"),
            List.of("MaidenBeloved", "OceanHuedClam", "SongOfDaysPast"),
            List.of("MartialArtist", "MarechausseeHunter"),
            List.of("TenacityOfTheMillelith", "VourukashasGlow")
    );
    private static final Map<String, Set<String>> TWO_PIECE_EQUIVALENTS = createIndex();

    private ArtifactSetEffectCatalog() {
    }

    public static Set<String> equivalentSetKeys(ArtifactSetRule rule) {
        if (rule.pieces() != 2) return Set.of(rule.setKey());
        return TWO_PIECE_EQUIVALENTS.getOrDefault(rule.setKey(), Set.of(rule.setKey()));
    }

    private static Map<String, Set<String>> createIndex() {
        Map<String, Set<String>> index = new LinkedHashMap<>();
        for (List<String> group : TWO_PIECE_GROUPS) {
            Set<String> equivalents = Collections.unmodifiableSet(new LinkedHashSet<>(group));
            group.forEach(setKey -> index.put(setKey, equivalents));
        }
        return Collections.unmodifiableMap(index);
    }
}
