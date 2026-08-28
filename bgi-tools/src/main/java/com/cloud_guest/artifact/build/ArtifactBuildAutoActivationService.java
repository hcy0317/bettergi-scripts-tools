package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.character.ArtifactCharacterRoster;
import com.cloud_guest.artifact.character.ArtifactCharacterRosterEntry;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArtifactBuildAutoActivationService {
    private final ArtifactBuildService buildService;
    private final ArtifactBuildAutoActivationResultRepository resultRepository;

    public ArtifactBuildAutoActivationService(
            ArtifactBuildService buildService,
            ArtifactBuildAutoActivationResultRepository resultRepository) {
        this.buildService = buildService;
        this.resultRepository = resultRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArtifactBuildAutoActivationResult apply(
            ArtifactCharacterRoster roster,
            ArtifactBuildAutoActivationSettings settings) {
        List<ArtifactCharacterRosterEntry> characters = roster.characters().stream()
                .sorted(Comparator.comparing(ArtifactCharacterRosterEntry::characterKey))
                .toList();
        ArtifactBuildAutoActivationResult previous = latest(roster.uid());
        RosterDifference difference = compare(previous, characters);
        boolean applied = previous == null || previous.characters().isEmpty()
                || difference.isEmpty();
        int favoriteCharacters = (int) characters.stream()
                .filter(character -> character.favorite()).count();
        int levelEligibleCharacters = (int) characters.stream()
                .filter(character -> character.level() >= settings.levelThreshold()).count();
        Set<String> eligibleCharacters = characters.stream()
                .filter(character -> character.level() >= settings.levelThreshold()
                        || (settings.favoriteOverride() && character.favorite()))
                .map(character -> character.characterKey())
                .collect(Collectors.toUnmodifiableSet());
        List<ArtifactBuild> current = buildService.list();
        List<String> appliedEligibleCharacterKeys = applied
                ? eligibleCharacters.stream().sorted().toList()
                : previous == null ? List.of() : previous.appliedEligibleCharacterKeys();
        Set<String> appliedEligibleCharacters = Set.copyOf(appliedEligibleCharacterKeys);
        List<ArtifactBuild> updated = current.stream()
                .map(build -> build.withActivation(
                        appliedEligibleCharacters.contains(build.characterKey())))
                .toList();
        int enabledBuilds = (int) updated.stream()
                .filter(ArtifactBuild::analysisEnabled).count();
        var result = new ArtifactBuildAutoActivationResult(
                characters.size(), favoriteCharacters, levelEligibleCharacters,
                eligibleCharacters.size(),
                enabledBuilds, updated.size() - enabledBuilds, settings,
                applied, digest(characters), characters,
                appliedEligibleCharacterKeys,
                difference.added(), difference.removed(), difference.changed());
        return resultRepository.save(roster.uid(), result);
    }

    public ArtifactBuildAutoActivationResult latest(String uid) {
        return resultRepository.find(uid).orElse(null);
    }

    public List<ArtifactBuild> resolve(
            String uid,
            List<ArtifactBuild> builds) {
        ArtifactBuildAutoActivationResult result = latest(uid);
        if (result == null) {
            return List.copyOf(builds);
        }
        if (result.rosterDigest() == null || result.rosterDigest().isBlank()) {
            return builds.stream().map(build -> build.withActivation(false)).toList();
        }
        Set<String> enabledCharacters = Set.copyOf(
                result.appliedEligibleCharacterKeys());
        return builds.stream()
                .map(build -> build.withActivation(
                        enabledCharacters.contains(build.characterKey())))
                .toList();
    }

    public boolean clear(String uid) {
        return resultRepository.delete(uid);
    }

    private static RosterDifference compare(
            ArtifactBuildAutoActivationResult previous,
            List<ArtifactCharacterRosterEntry> current) {
        if (previous == null || previous.characters().isEmpty()) {
            return new RosterDifference(List.of(), List.of(), List.of());
        }
        Map<String, ArtifactCharacterRosterEntry> before = previous.characters().stream()
                .collect(Collectors.toMap(
                        ArtifactCharacterRosterEntry::characterKey, Function.identity()));
        Map<String, ArtifactCharacterRosterEntry> after = current.stream()
                .collect(Collectors.toMap(
                        ArtifactCharacterRosterEntry::characterKey, Function.identity()));
        List<String> added = after.keySet().stream()
                .filter(key -> !before.containsKey(key)).sorted().toList();
        List<String> removed = before.keySet().stream()
                .filter(key -> !after.containsKey(key)).sorted().toList();
        List<String> changed = after.entrySet().stream()
                .filter(entry -> before.containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().equals(before.get(entry.getKey())))
                .map(Map.Entry::getKey).sorted().toList();
        return new RosterDifference(added, removed, changed);
    }

    private static String digest(List<ArtifactCharacterRosterEntry> characters) {
        String canonical = characters.stream()
                .map(character -> character.characterKey() + ":" + character.level()
                        + ":" + character.favorite())
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RosterDifference(
            List<String> added,
            List<String> removed,
            List<String> changed) {
        boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
        }
    }
}
