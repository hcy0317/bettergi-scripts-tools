package com.cloud_guest.artifact.build;

import com.cloud_guest.artifact.character.ArtifactCharacterRosterEntry;

import java.util.List;

public record ArtifactBuildAutoActivationResult(
        int characterCount,
        int favoriteCharacterCount,
        int levelEligibleCharacterCount,
        int eligibleCharacterCount,
        int enabledBuildCount,
        int disabledBuildCount,
        ArtifactBuildAutoActivationSettings settings,
        Boolean applied,
        String rosterDigest,
        List<ArtifactCharacterRosterEntry> characters,
        List<String> appliedEligibleCharacterKeys,
        List<String> addedCharacterKeys,
        List<String> removedCharacterKeys,
        List<String> changedCharacterKeys) {

    public ArtifactBuildAutoActivationResult {
        characters = characters == null ? List.of() : List.copyOf(characters);
        appliedEligibleCharacterKeys = appliedEligibleCharacterKeys == null
                ? List.of() : List.copyOf(appliedEligibleCharacterKeys);
        addedCharacterKeys = addedCharacterKeys == null ? List.of() : List.copyOf(addedCharacterKeys);
        removedCharacterKeys = removedCharacterKeys == null ? List.of() : List.copyOf(removedCharacterKeys);
        changedCharacterKeys = changedCharacterKeys == null ? List.of() : List.copyOf(changedCharacterKeys);
    }
}
