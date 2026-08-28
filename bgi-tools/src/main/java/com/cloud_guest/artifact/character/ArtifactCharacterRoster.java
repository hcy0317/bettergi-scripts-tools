package com.cloud_guest.artifact.character;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ArtifactCharacterRoster(
        String uid,
        List<ArtifactCharacterRosterEntry> characters) {

    public ArtifactCharacterRoster {
        if (uid == null || !uid.matches("[0-9]{6,12}")) {
            throw new IllegalArgumentException("valid uid is required");
        }
        characters = characters == null ? List.of() : List.copyOf(characters);
        if (characters.isEmpty()) {
            throw new IllegalArgumentException("complete character roster cannot be empty");
        }
        Set<String> keys = new HashSet<>();
        if (characters.stream().anyMatch(character -> !keys.add(character.characterKey()))) {
            throw new IllegalArgumentException("character roster keys must be unique");
        }
    }
}
