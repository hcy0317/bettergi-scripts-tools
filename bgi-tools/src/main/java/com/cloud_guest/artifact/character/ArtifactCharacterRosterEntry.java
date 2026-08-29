package com.cloud_guest.artifact.character;

public record ArtifactCharacterRosterEntry(
        String characterKey,
        int level,
        boolean favorite) {

    public ArtifactCharacterRosterEntry {
        if (characterKey == null || !characterKey.matches("[A-Za-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("valid character key is required");
        }
        if (level < 1 || level > 90) {
            throw new IllegalArgumentException("character level must be between 1 and 90");
        }
    }
}
