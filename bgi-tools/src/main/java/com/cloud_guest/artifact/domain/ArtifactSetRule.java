package com.cloud_guest.artifact.domain;

public record ArtifactSetRule(String setKey, int pieces) {
    public ArtifactSetRule {
        if (setKey == null || setKey.isBlank()) throw new IllegalArgumentException("set key is required");
        if (pieces != 2 && pieces != 4) throw new IllegalArgumentException("set pieces must be 2 or 4");
    }
}
