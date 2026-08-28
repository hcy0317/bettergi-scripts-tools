package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.character.ArtifactCharacterRoster;
import com.cloud_guest.artifact.domain.ArtifactBuild;
import org.springframework.stereotype.Service;

import java.util.Set;
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

    public ArtifactBuildAutoActivationResult apply(
            ArtifactCharacterRoster roster,
            ArtifactBuildAutoActivationSettings settings) {
        int favoriteCharacters = (int) roster.characters().stream()
                .filter(character -> character.favorite()).count();
        int levelEligibleCharacters = (int) roster.characters().stream()
                .filter(character -> character.level() >= settings.levelThreshold()).count();
        Set<String> eligibleCharacters = roster.characters().stream()
                .filter(character -> character.level() >= settings.levelThreshold()
                        || (settings.favoriteOverride() && character.favorite()))
                .map(character -> character.characterKey())
                .collect(Collectors.toUnmodifiableSet());
        var updated = buildService.list().stream()
                .map(build -> build.withActivation(eligibleCharacters.contains(build.characterKey())))
                .toList();
        buildService.importAll(updated);
        int enabledBuilds = (int) updated.stream().filter(ArtifactBuild::analysisEnabled).count();
        var result = new ArtifactBuildAutoActivationResult(
                roster.characters().size(), favoriteCharacters, levelEligibleCharacters,
                eligibleCharacters.size(),
                enabledBuilds, updated.size() - enabledBuilds, settings);
        return resultRepository.save(roster.uid(), result);
    }

    public ArtifactBuildAutoActivationResult latest(String uid) {
        return resultRepository.find(uid).orElse(null);
    }
}
